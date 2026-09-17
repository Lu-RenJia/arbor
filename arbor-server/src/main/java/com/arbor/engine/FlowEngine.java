package com.arbor.engine;

import com.arbor.common.AssertUtils;
import com.arbor.common.ErrorCode;
import com.arbor.engine.executor.StartNodeExecutor;
import com.arbor.entity.ExecutionLog;
import com.arbor.entity.ExecutionNodeLog;
import com.arbor.entity.FlowNode;
import com.arbor.groovy.log.ScriptLogCollector;
import com.arbor.groovy.log.ScriptLogContext;
import com.arbor.groovy.log.ScriptLogEntry;
import com.arbor.mapper.ExecutionLogMapper;
import com.arbor.mapper.ExecutionNodeLogMapper;
import com.arbor.mapper.FlowMapper;
import com.arbor.mapper.FlowNodeMapper;
import com.arbor.service.ExecutionScriptLogService;
import com.arbor.service.GlobalParamService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 流程执行引擎
 * 按节点顺序逐个执行，节点间数据通过内存 Map 传递，某节点报错则终止流程
 *
 * @author gwl
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FlowEngine {

    /** 执行方式 - 手动 */
    public static final String EXECUTE_MANUAL = "MANUAL";
    /** 执行方式 - 自动 */
    public static final String EXECUTE_AUTO = "AUTO";

    /** 日志来源 - 测试流程 */
    public static final String SOURCE_FLOW_TEST = "FLOW_TEST";
    /** 日志来源 - 执行应用 */
    public static final String SOURCE_APP = "APP";
    /** 日志来源 - 手动执行 */
    public static final String SOURCE_FLOW_MANUAL = "FLOW_MANUAL";
    /** 日志来源 - 自动执行 */
    public static final String SOURCE_FLOW_AUTO = "FLOW_AUTO";
    /** 日志来源 - 脚本独立测试（DB脚本/程序脚本/API/Redis） */
    public static final String SOURCE_SCRIPT_TEST = "SCRIPT_TEST";

    private final FlowMapper flowMapper;
    private final FlowNodeMapper flowNodeMapper;
    private final ExecutionLogMapper executionLogMapper;
    private final ExecutionNodeLogMapper executionNodeLogMapper;
    private final ExecutionScriptLogService executionScriptLogService;
    private final GlobalParamService globalParamService;
    private final NodeRegistry nodeRegistry;
    private final ParamResolver paramResolver;
    private final ObjectMapper objectMapper;
    private final RedisGetSetSupport redisGetSetSupport;
    private final StopConditionSupport stopConditionSupport;
    private final com.arbor.groovy.GroovyScriptEngine groovyScriptEngine;
    private final FileOutputHandler fileOutputHandler;
    private final FlowCancelRegistry cancelRegistry;
    private final RetrySupport retrySupport;
    private final PlatformTransactionManager transactionManager;

    /**
     * 执行流程。
     * <p>事务边界：节点执行期间（可能含长耗时外部调用）不持有数据库事务，
     * 仅执行日志落库阶段用短事务保证主日志与节点日志原子写入，
     * 避免长事务占住连接池连接导致并发执行时连接耗尽。
     * </p>
     *
     * @param flowId      流程ID
     * @param executeType 执行方式（MANUAL/AUTO）
     * @param source      日志来源（FLOW_TEST/APP/FLOW_MANUAL/FLOW_AUTO）
     * @return 执行结果汇总
     */
    public FlowExecutionResult execute(Long flowId, String executeType, String source) {
        // 开启本次执行的脚本日志采集（子流程/分支/重试同线程，日志自然归入同一个收集器）
        ScriptLogCollector scriptLogCollector = ScriptLogContext.open();
        try {
            return doExecute(flowId, executeType, source, scriptLogCollector);
        } finally {
            // 必须解绑：定时任务与 Web 线程都会复用，漏解绑会让下一次执行串到上一次的收集器里
            ScriptLogContext.close();
        }
    }

    /** 真正的执行体（调用方已开启脚本日志采集上下文） */
    private FlowExecutionResult doExecute(Long flowId, String executeType, String source,
                                          ScriptLogCollector scriptLogCollector) {
        var flow = flowMapper.selectById(flowId);
        AssertUtils.notNull(flow, ErrorCode.FLOW_NOT_FOUND);
        // 自动执行需检查流程是否启用，手动执行不检查
        AssertUtils.isFalse(!EXECUTE_MANUAL.equals(executeType) && flow.getStatus() != 1, ErrorCode.FLOW_CLOSED);

        // 获取流程节点（按 sort_order 排序），分离主线/分支节点
        List<FlowNode> allNodes = flowNodeMapper.selectByFlowId(flowId);
        AssertUtils.isFalse(allNodes.isEmpty(), ErrorCode.FLOW_NO_NODES);
        List<FlowNode> nodes = allNodes.stream().filter(n -> n.getBranchOf() == null).collect(java.util.stream.Collectors.toList());
        java.util.Map<Long, List<FlowNode>> branchMap = allNodes.stream()
                .filter(n -> n.getBranchOf() != null)
                .collect(java.util.stream.Collectors.groupingBy(FlowNode::getBranchOf));

        // 获取全局参数
        Map<String, String> globalParams = globalParamService.getAllParams();

        log.info("开始执行流程: id={}, name={}, 节点数={}, 方式={}",
                flowId, flow.getName(), nodes.size(), executeType);

        LocalDateTime startTime = LocalDateTime.now();
        Map<String, NodeResult> previousResults = new LinkedHashMap<>();
        // 顺序记录每个节点的执行结果（含耗时），供前端展示
        List<NodeResult> nodeResultList = new ArrayList<>();
        String errorMessage = null;
        boolean success = true;
        // 停止条件触发标记 + 停止原因（优雅停止，非失败）
        boolean flowStopped = false;
        String stopMessage = null;
        String stopType = null;

        // 注册取消标记：用户可在执行中取消，每个节点执行前检查（当前节点不打断，后续节点不再执行）
        Long cancelUserId = com.arbor.common.TenantContext.getUserId();
        cancelRegistry.start(cancelUserId, flowId);
        boolean cancelled = false;

        // 按顺序执行每个节点
        for (FlowNode node : nodes) {
            if (cancelRegistry.isCancelled(cancelUserId, flowId)) {
                cancelled = true;
                log.info("流程执行已被用户取消: id={}, 未执行节点={}", flowId, node.getNodeCode());
                break;
            }
            log.info("执行节点: code={}, name={}, type={}", node.getNodeCode(), node.getNodeName(), node.getNodeType());
            // 更新脚本日志归属，此后本节点内脚本打的日志都记在这个节点名下
            ScriptLogContext.currentNode(node.getNodeCode(), node.getNodeName());

            long nodeStart = System.currentTimeMillis();
            try {
                String nodeType = node.getNodeType();
                boolean isStructural = "START".equals(nodeType) || "END".equals(nodeType);

                // START/END 节点没有业务配置，跳过参数解析；停止条件与重试字段属引擎级配置，先剥离再解析
                // （停止表达式可能引用当前节点自身，执行前尚未产生结果，预解析会报错）
                String resolvedConfig = isStructural
                        ? node.getConfig()
                        : paramResolver.resolve(
                                retrySupport.stripRetryFields(stopConditionSupport.stripStopFields(node.getConfig())),
                                globalParams, previousResults);

                // 构建执行上下文
                NodeExecutionContext context = NodeExecutionContext.builder()
                        .flowId(flowId)
                        .nodeId(node.getId())
                        .nodeCode(node.getNodeCode())
                        .nodeName(node.getNodeName())
                        .nodeType(nodeType)
                        .config(resolvedConfig)
                        .rawConfig(node.getConfig())
                        .globalParams(globalParams)
                        .previousResults(previousResults)
                        .build();

                // 获取对应类型的执行器并执行（节点可配置重试：失败/异常按策略重试）
                NodeExecutor executor = nodeRegistry.get(nodeType);
                NodeResult result = retrySupport.executeWithRetry(executor, context, node.getConfig());

                // 记录节点信息 + 耗时
                long nodeDuration = System.currentTimeMillis() - nodeStart;
                NodeResult timedResult = NodeResult.of(
                        node.getNodeCode(), node.getNodeName(), nodeType,
                        result.isSuccess(), result.getData(), result.getErrorMessage(), nodeDuration);
                if (result.getSubFlowNodeDetails() != null) {
                    timedResult.setSubFlowNodeDetails(result.getSubFlowNodeDetails());
                }
                previousResults.put(node.getNodeCode(), timedResult);
                nodeResultList.add(timedResult);

                // START 节点入参作为特殊 key 供后续节点直接 ${_input} 引用
                if ("START".equals(nodeType) && result.isSuccess() && result.getData() != null) {
                    Object startData = result.getData();
                    if (startData instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> startMap = (Map<String, Object>) startData;
                        // _input（新）/ flow_param（旧兼容）
                        Object input = startMap.get(StartNodeExecutor.FLOW_PARAM_KEY);
                        if (input != null) {
                            previousResults.put(StartNodeExecutor.FLOW_PARAM_KEY,
                                    NodeResult.of(StartNodeExecutor.FLOW_PARAM_KEY, "入参", "PARAM", true, input, null, 0));
                        }
                        // 测试文件：解码后注入 _fileStream + _fileName
                        byte[] fileBytes = (byte[]) startMap.get("_testFileBytes");
                        if (fileBytes != null) {
                            previousResults.put("_fileStream",
                                    NodeResult.of("_fileStream", "上传文件流", "PARAM", true, new java.io.ByteArrayInputStream(fileBytes), null, 0));
                            previousResults.put("_fileName",
                                    NodeResult.of("_fileName", "上传文件名", "PARAM", true, startMap.get("_testFileName"), null, 0));
                        }
                    } else {
                        previousResults.put(StartNodeExecutor.FLOW_PARAM_KEY,
                                NodeResult.of(StartNodeExecutor.FLOW_PARAM_KEY, "入参", "PARAM", true, startData, null, 0));
                    }
                }

                // REDIS 节点：GET&SET 配置且缓存未命中 → 执行分支流程，最后一个分支节点结果回写为
                // REDIS 节点结果并按配置 key/ttl 回填缓存（只有 GET&SET 命令的配置才触发分支）
                if ("REDIS".equals(nodeType) && result.isSuccess() && result.getData() == null) {
                    if (redisGetSetSupport.isGetSetNode(node.getConfig())) {
                        List<FlowNode> branches = branchMap.get(node.getId());
                        if (branches == null || branches.isEmpty()) {
                            // 没有分支且缓存未命中不算错误，继续执行
                        } else if (redisGetSetSupport.getSetTtlSeconds(node.getConfig()) <= 0) {
                            success = false;
                            errorMessage = "Redis 节点「" + node.getNodeName() + "」的 GET&SET 配置未设置有效过期时间，无法执行分支流程";
                            log.warn(errorMessage);
                            break;
                        } else {
                            log.info("Redis GET&SET 未命中，执行分支流程: 节点数={}", branches.size());
                            NodeResult lastBranchResult = null;
                            for (FlowNode bn : branches) {
                                if (cancelRegistry.isCancelled(cancelUserId, flowId)) { cancelled = true; break; }
                                long bnStart = System.currentTimeMillis();
                                try {
                                    String bnConfig = paramResolver.resolve(bn.getConfig(), globalParams, previousResults);
                                    // 分支节点同样更新脚本日志归属
                                    ScriptLogContext.currentNode(bn.getNodeCode(), bn.getNodeName());
                                    NodeExecutionContext bnCtx = NodeExecutionContext.builder()
                                            .flowId(flowId).nodeId(bn.getId()).nodeCode(bn.getNodeCode())
                                            .nodeName(bn.getNodeName()).nodeType(bn.getNodeType())
                                            .config(bnConfig).rawConfig(bn.getConfig())
                                            .globalParams(globalParams).previousResults(previousResults).build();
                                    NodeExecutor bnExec = nodeRegistry.get(bn.getNodeType());
                                    NodeResult bnResult = retrySupport.executeWithRetry(bnExec, bnCtx, bn.getConfig());
                                    long bnDur = System.currentTimeMillis() - bnStart;
                                    NodeResult bnTimed = NodeResult.of(bn.getNodeCode(), bn.getNodeName(), bn.getNodeType(),
                                            bnResult.isSuccess(), bnResult.getData(), bnResult.getErrorMessage(), bnDur);
                                    previousResults.put(bn.getNodeCode(), bnTimed);
                                    nodeResultList.add(bnTimed);
                                    if (!bnResult.isSuccess()) {
                                        success = false;
                                        errorMessage = "分支节点「" + bn.getNodeName() + "」(" + bn.getNodeCode() + ") 执行失败: " + bnResult.getErrorMessage();
                                        log.warn(errorMessage);
                                        break;
                                    }
                                    lastBranchResult = bnTimed;
                                } catch (Exception ex) {
                                    success = false;
                                    errorMessage = "分支节点「" + bn.getNodeName() + "」(" + bn.getNodeCode() + ") 执行异常: " + ex.getMessage();
                                    log.error("分支节点异常: code={}", bn.getNodeCode(), ex);
                                    break;
                                }
                            }
                            if (!success || cancelled) break;
                            if (lastBranchResult != null) {
                                // 回写同一 timedResult 实例：previousResults 与 nodeResultList 同引用，两边同时生效
                                redisGetSetSupport.writeBackAndCache(node.getConfig(), timedResult, lastBranchResult.getData(),
                                        globalParams, previousResults);
                            }
                        }
                    }
                }

                if (!result.isSuccess()) {
                    success = false;
                    errorMessage = "节点「" + node.getNodeName() + "」(" + node.getNodeCode() + ") 执行失败: " + result.getErrorMessage();
                    log.warn("节点执行失败: code={}, error={}", node.getNodeCode(), result.getErrorMessage());
                    break;
                }

                // 主线节点执行成功后评估停止条件（分支节点不评估）；
                // 表达式出错按流程失败处理，节点本身保持成功状态
                if (!isStructural) {
                    StopConditionSupport.StopResult stopResult;
                    try {
                        stopResult = stopConditionSupport.evaluate(node.getConfig(), timedResult, globalParams, previousResults);
                    } catch (Exception se) {
                        success = false;
                        errorMessage = "节点「" + node.getNodeName() + "」(" + node.getNodeCode() + ") 停止条件评估失败: " + se.getMessage();
                        log.error("停止条件评估失败: code={}", node.getNodeCode(), se);
                        break;
                    }
                    if (stopResult != null) {
                        flowStopped = true;
                        stopMessage = stopResult.getMessage();
                        stopType = stopResult.getType();
                        log.info("流程停止: 节点 {} 触发{}停止条件, 原因={}", node.getNodeCode(),
                                StopConditionSupport.STOP_TYPE_NORMAL.equals(stopType) ? "正常" : "异常", stopResult.getMessage());
                        break;
                    }
                }

                log.info("节点执行成功: code={}, dataType={}",
                        node.getNodeCode(),
                        result.getData() != null ? result.getData().getClass().getSimpleName() : "null");

            } catch (Exception e) {
                success = false;
                errorMessage = "节点「" + node.getNodeName() + "」(" + node.getNodeCode() + ") 执行异常: " + e.getMessage();
                long failDuration = System.currentTimeMillis() - nodeStart;
                NodeResult failResult = NodeResult.of(
                        node.getNodeCode(), node.getNodeName(), node.getNodeType(),
                        false, null, errorMessage, failDuration);
                previousResults.put(node.getNodeCode(), failResult);
                nodeResultList.add(failResult);
                log.error("节点执行异常: code={}", node.getNodeCode(), e);
                break;
            }
        }

        // 停止条件触发/用户取消：剩余未执行的主线节点以 STOPPED 占位补入结果列表（前端执行列表展示全部节点）
        if (flowStopped || cancelled) {
            for (FlowNode remaining : nodes) {
                if (!previousResults.containsKey(remaining.getNodeCode())) {
                    nodeResultList.add(NodeResult.stopped(
                            remaining.getNodeCode(), remaining.getNodeName(), remaining.getNodeType()));
                }
            }
        }
        if (cancelled) {
            success = false;
            errorMessage = "用户取消了执行";
        }

        LocalDateTime endTime = LocalDateTime.now();
        long duration = java.time.Duration.between(startTime, endTime).toMillis();

        String flowStatus = cancelled ? NodeResult.STATUS_CANCELLED
                : (flowStopped ? NodeResult.STATUS_STOPPED
                : (success ? NodeResult.STATUS_SUCCESS : NodeResult.STATUS_FAIL));
        log.info("流程执行完成: id={}, status={}, duration={}ms", flowId, flowStatus, duration);

        // 本次执行捕获的脚本日志（节点循环已结束，此后不再有脚本执行）
        List<ScriptLogEntry> scriptLogs = scriptLogCollector.snapshot();

        // 保存执行日志（短事务：主日志与节点日志原子写入；STOPPED 时 error_message 存停止原因，供日志页展示）
        final String persistErrorMessage = flowStopped ? stopMessage : errorMessage;
        final String persistStopType = flowStopped ? stopType : null;
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status -> {
            ExecutionLog execLog = new ExecutionLog();
            execLog.setFlowId(flowId);
            execLog.setFlowName(flow.getName());
            execLog.setUserId(com.arbor.common.TenantContext.getUserId());
            execLog.setExecuteType(executeType);
            execLog.setSource(source);
            execLog.setOwnerId(flow.getUserId());
            execLog.setExecutorId(com.arbor.common.TenantContext.getUserId());
            execLog.setStatus(flowStatus);
            execLog.setStartTime(startTime);
            execLog.setEndTime(endTime);
            execLog.setDuration(duration);
            execLog.setErrorMessage(persistErrorMessage);
            execLog.setStopType(persistStopType);
            executionLogMapper.insert(execLog);

            // 保存各节点执行日志（按执行顺序遍历结果集）
            for (Map.Entry<String, NodeResult> entry : previousResults.entrySet()) {
                String nodeCode = entry.getKey();
                NodeResult nodeResult = entry.getValue();
                FlowNode node = allNodes.stream()
                        .filter(n -> nodeCode.equals(n.getNodeCode()))
                        .findFirst().orElse(null);

                ExecutionNodeLog nodeLog = new ExecutionNodeLog();
                nodeLog.setExecutionLogId(execLog.getId());
                nodeLog.setNodeId(node != null ? node.getId() : null);
                nodeLog.setNodeCode(nodeCode);
                nodeLog.setNodeName(node != null ? node.getNodeName() : nodeCode);
                nodeLog.setNodeType(node != null ? node.getNodeType() : "");
                nodeLog.setSortOrder(node != null ? node.getSortOrder() : 0);
                nodeLog.setStatus(nodeResult.getStatus() != null ? nodeResult.getStatus()
                        : (nodeResult.isSuccess() ? NodeResult.STATUS_SUCCESS : NodeResult.STATUS_FAIL));
                nodeLog.setErrorMessage(nodeResult.getErrorMessage());
                // 只保留子流程内部节点状态（历史详情要展示）；节点返回数据不再落库 ——
                // 大返回值会撞 MySQL JSON 列的 max_allowed_packet，且详情已不再渲染它，
                // 需要观察的数据由脚本自己 log 出来
                if (nodeResult.getSubFlowNodeDetails() != null) {
                    try {
                        Map<String, Object> wrapped = new java.util.LinkedHashMap<>();
                        wrapped.put("subFlowNodes", nodeResult.getSubFlowNodeDetails());
                        nodeLog.setOutputResult(objectMapper.writeValueAsString(wrapped));
                    } catch (Exception ignored) {
                        nodeLog.setOutputResult(null);
                    }
                } else {
                    nodeLog.setOutputResult(null);
                }
                executionNodeLogMapper.insert(nodeLog);
            }

            // 停止条件触发时，未执行节点也落 STOPPED 日志行（日志详情展示全部节点）
            for (NodeResult stoppedResult : nodeResultList) {
                if (!NodeResult.STATUS_STOPPED.equals(stoppedResult.getStatus())) {
                    continue;
                }
                FlowNode node = allNodes.stream()
                        .filter(n -> stoppedResult.getNodeCode().equals(n.getNodeCode()))
                        .findFirst().orElse(null);
                ExecutionNodeLog nodeLog = new ExecutionNodeLog();
                nodeLog.setExecutionLogId(execLog.getId());
                nodeLog.setNodeId(node != null ? node.getId() : null);
                nodeLog.setNodeCode(stoppedResult.getNodeCode());
                nodeLog.setNodeName(stoppedResult.getNodeName());
                nodeLog.setNodeType(stoppedResult.getNodeType());
                nodeLog.setSortOrder(node != null ? node.getSortOrder() : 0);
                nodeLog.setStatus(NodeResult.STATUS_STOPPED);
                executionNodeLogMapper.insert(nodeLog);
            }

            // 脚本日志与主日志/节点日志同事务（内部已兜异常，写失败不会回滚主日志）
            executionScriptLogService.save(execLog.getId(), scriptLogs);
        });

        // 清理流程级 class 缓存
        groovyScriptEngine.evictFlowClasses(flowId);

        // FILE 输出模式：检测 START 节点 outputType，自动导出最后节点数据为 Excel
        FlowExecutionResult.OutputFileInfo outputFile = null;
        if (success || flowStopped) {
            FlowNode startNode = allNodes.stream()
                    .filter(n -> "START".equals(n.getNodeType()))
                    .findFirst().orElse(null);
            if (startNode != null) {
                try {
                    Map<String, Object> startCfg = objectMapper.readValue(startNode.getConfig(), Map.class);
                    outputFile = fileOutputHandler.handleFileOutput(
                            nodeResultList, startCfg, flowId, flow.getName());
                } catch (Exception e) {
                    log.error("FILE 输出模式: 解析 START 配置失败", e);
                }
            }
            // 兜底：非 FILE 模式下最后节点结果本身是文件（API 二进制响应/脚本协议）时自动生成下载
            if (outputFile == null) {
                outputFile = fileOutputHandler.detectFileOutput(nodeResultList, flowId, flow.getName());
            }
        }

        // 清理取消标记（异常路径未走到此处时，靠注册表双时间戳比较兜底，不会误杀下次执行）
        cancelRegistry.finish(cancelUserId, flowId);

        return FlowExecutionResult.builder()
                .flowId(flowId)
                .flowName(flow.getName())
                .executeType(executeType)
                .success(success)
                .status(flowStatus)
                .stopMessage(stopMessage)
                .stopType(stopType)
                .outputFile(outputFile)
                .startTime(startTime)
                .endTime(endTime)
                .duration(duration)
                .errorMessage(errorMessage)
                .nodeDetailList(nodeResultList)   // 按执行顺序的节点详情（含耗时）
                .scriptLogs(scriptLogs)           // 本次执行捕获的脚本日志（按时间顺序）
                .build();
    }
}
