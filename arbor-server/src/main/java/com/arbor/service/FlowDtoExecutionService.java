package com.arbor.service;

import com.arbor.common.AssertUtils;
import com.arbor.common.ErrorCode;
import com.arbor.common.TenantContext;
import com.arbor.dto.FlowSaveDTO;
import com.arbor.engine.FlowEngine;
import com.arbor.engine.FlowCancelRegistry;
import com.arbor.engine.FlowExecutionResult;
import com.arbor.engine.NodeExecutionContext;
import com.arbor.engine.NodeExecutor;
import com.arbor.engine.NodeRegistry;
import com.arbor.engine.NodeResult;
import com.arbor.engine.ParamResolver;
import com.arbor.engine.RedisGetSetSupport;
import com.arbor.engine.RetrySupport;
import com.arbor.engine.StopConditionSupport;
import com.arbor.engine.executor.StartNodeExecutor;
import com.arbor.entity.Flow;
import com.arbor.groovy.log.ScriptLogCollector;
import com.arbor.groovy.log.ScriptLogContext;
import com.arbor.groovy.log.ScriptLogEntry;
import com.arbor.mapper.ExecutionLogMapper;
import com.arbor.mapper.ExecutionNodeLogMapper;
import com.arbor.mapper.FlowMapper;
import com.arbor.util.RedisCacheUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 流程 DTO 路径执行服务（测试流程/应用执行专用）
 * <p>
 * 与 {@link FlowEngine}（DB 路径）同构：不持久化节点，直接用传入的节点 DTO 列表执行。
 * 从 FlowService 拆出，使 FlowService 专注流程 CRUD；
 * 重试、停止条件、分支回写等引擎能力与 DB 路径保持一致。
 * </p>
 * <p>事务边界：节点执行期间（可能含长耗时外部调用）不持有数据库事务，
 * 仅执行日志落库阶段用短事务保证主日志与节点日志原子写入。</p>
 *
 * @author gwl
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlowDtoExecutionService {

    private final FlowMapper flowMapper;
    private final GlobalParamService globalParamService;
    private final NodeRegistry nodeRegistry;
    private final ParamResolver paramResolver;
    private final RedisCacheUtil redisCacheUtil;
    private final RedisGetSetSupport redisGetSetSupport;
    private final StopConditionSupport stopConditionSupport;
    private final RetrySupport retrySupport;
    private final com.arbor.groovy.GroovyScriptEngine groovyScriptEngine;
    private final com.arbor.engine.FileOutputHandler fileOutputHandler;
    private final FlowCancelRegistry cancelRegistry;
    private final ExecutionLogMapper executionLogMapper;
    private final ExecutionNodeLogMapper executionNodeLogMapper;
    private final ExecutionScriptLogService executionScriptLogService;
    private final ObjectMapper objectMapper;
    private final PlatformTransactionManager transactionManager;

    /** 不持久化，直接用传入的节点列表执行 */
    public FlowExecutionResult executeWithNodes(Long flowId, List<FlowSaveDTO.FlowNodeDTO> nodeDTOs) {
        return executeWithNodes(flowId, nodeDTOs, null, true);
    }

    /** 执行流程（使用预加载的 Flow，避免租户二次查询失败） */
    public FlowExecutionResult executeWithNodes(Long flowId, List<FlowSaveDTO.FlowNodeDTO> nodeDTOs, Flow flow) {
        return executeWithNodes(flowId, nodeDTOs, flow, false);
    }

    /**
     * 执行流程（内部方法）：只负责开启/关闭本次执行的脚本日志采集。
     * <p>
     * 采集用 ThreadLocal 承载，必须 try/finally 解绑 —— 线程池复用下漏解绑会让
     * 下一次执行的日志串到上一次的收集器里（既是脏数据也是内存泄漏）。
     * </p>
     *
     * @param writeLog true=写 FLOW_TEST 日志（测试流程），false=不写（由调用方自行记录，如应用执行）
     */
    private FlowExecutionResult executeWithNodes(Long flowId, List<FlowSaveDTO.FlowNodeDTO> nodeDTOs,
                                                  Flow flow, boolean writeLog) {
        ScriptLogCollector scriptLogCollector = ScriptLogContext.open();
        try {
            return doExecuteWithNodes(flowId, nodeDTOs, flow, writeLog, scriptLogCollector);
        } finally {
            ScriptLogContext.close();
        }
    }

    /** 真正的执行体（调用方已开启脚本日志采集上下文） */
    private FlowExecutionResult doExecuteWithNodes(Long flowId, List<FlowSaveDTO.FlowNodeDTO> nodeDTOs,
                                                    Flow flow, boolean writeLog,
                                                    ScriptLogCollector scriptLogCollector) {
        if (flow == null) flow = getFlowCached(flowId);
        AssertUtils.notNull(flow, ErrorCode.FLOW_NOT_FOUND);
        AssertUtils.isTrue(nodeDTOs != null && !nodeDTOs.isEmpty(), ErrorCode.FLOW_NO_NODES);

        Map<String, String> globalParams = globalParamService.getAllParams();
        log.info("开始执行流程(测试): id={}, name={}, 节点数={}", flowId, flow.getName(), nodeDTOs.size());

        LocalDateTime startTime = LocalDateTime.now();
        Map<String, NodeResult> previousResults = new LinkedHashMap<>();
        List<NodeResult> nodeResultList = new ArrayList<>();
        String errorMessage = null;
        boolean success = true;
        // 停止条件触发标记 + 停止原因（优雅停止，非失败）
        boolean flowStopped = false;
        String stopMessage = null;
        String stopType = null;

        // 注册取消标记：用户可在执行中取消，每个节点执行前检查（当前节点不打断，后续节点不再执行）
        Long cancelUserId = TenantContext.getUserId();
        cancelRegistry.start(cancelUserId, flowId);
        boolean cancelled = false;

        for (FlowSaveDTO.FlowNodeDTO dto : nodeDTOs) {
            if (cancelRegistry.isCancelled(cancelUserId, flowId)) {
                cancelled = true;
                log.info("流程测试执行已被用户取消: id={}, 未执行节点={}", flowId, dto.getNodeCode());
                break;
            }
            // 分支节点由 REDIS 节点回调执行，主线循环跳过
            if (dto.getBranchOfCode() != null && !dto.getBranchOfCode().isBlank()) continue;
            String code = dto.getNodeCode();
            String name = dto.getNodeName();
            String type = dto.getNodeType();
            log.info("执行节点: code={}, name={}, type={}", code, name, type);
            // 更新脚本日志归属，此后本节点内脚本打的日志都记在这个节点名下
            ScriptLogContext.currentNode(code, name);

            long nodeStart = System.currentTimeMillis();
            try {
                // 停止条件与重试字段属引擎级配置，先剥离再解析（表达式可能引用当前节点自身，预解析会报错）
                String resolvedConfig = paramResolver.resolve(
                        retrySupport.stripRetryFields(stopConditionSupport.stripStopFields(dto.getConfig())),
                        globalParams, previousResults);
                NodeExecutionContext context = NodeExecutionContext.builder()
                        .flowId(flowId)
                        .nodeId(null)
                        .nodeCode(code)
                        .nodeName(name)
                        .nodeType(type)
                        .config(resolvedConfig)
                        .rawConfig(dto.getConfig())
                        .globalParams(globalParams)
                        .previousResults(previousResults)
                        .build();

                // 获取对应类型的执行器并执行（节点可配置重试：失败/异常按策略重试）
                NodeExecutor executor = nodeRegistry.get(type);
                NodeResult result = retrySupport.executeWithRetry(executor, context, dto.getConfig());

                long nodeDuration = System.currentTimeMillis() - nodeStart;
                NodeResult timedResult = NodeResult.of(code, name, type,
                        result.isSuccess(), result.getData(), result.getErrorMessage(), nodeDuration);
                if (result.getSubFlowNodeDetails() != null) {
                    timedResult.setSubFlowNodeDetails(result.getSubFlowNodeDetails());
                }
                previousResults.put(code, timedResult);
                nodeResultList.add(timedResult);

                // START 节点入参作为特殊 key 供后续节点直接 ${_input} 引用
                if ("START".equals(type) && result.isSuccess() && result.getData() != null) {
                    Object startData = result.getData();
                    if (startData instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> startMap = (Map<String, Object>) startData;
                        Object input = startMap.get(StartNodeExecutor.FLOW_PARAM_KEY);
                        if (input != null) {
                            previousResults.put(StartNodeExecutor.FLOW_PARAM_KEY,
                                    NodeResult.of(StartNodeExecutor.FLOW_PARAM_KEY, "入参", "PARAM", true, input, null, 0));
                        }
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

                // REDIS 节点：GET&SET 配置且缓存未命中 → 执行分支 DTO 节点，最后一个分支节点结果
                // 回写为 REDIS 节点结果并按配置 key/ttl 回填缓存（与 FlowEngine DB 路径同构）
                if ("REDIS".equals(type) && result.isSuccess() && result.getData() == null) {
                    if (redisGetSetSupport.isGetSetNode(dto.getConfig())) {
                        List<FlowSaveDTO.FlowNodeDTO> branches = nodeDTOs.stream()
                                .filter(d -> dto.getNodeCode().equals(d.getBranchOfCode()))
                                .collect(java.util.stream.Collectors.toList());
                        if (!branches.isEmpty()) {
                            if (redisGetSetSupport.getSetTtlSeconds(dto.getConfig()) <= 0) {
                                success = false;
                                errorMessage = "Redis 节点「" + name + "」的 GET&SET 配置未设置有效过期时间，无法执行分支流程";
                                log.warn(errorMessage);
                                break;
                            }
                            NodeResult lastBranchResult = null;
                            for (FlowSaveDTO.FlowNodeDTO bdto : branches) {
                                if (cancelRegistry.isCancelled(cancelUserId, flowId)) { cancelled = true; break; }
                                long bnStart = System.currentTimeMillis();
                                try {
                                    String bnConfig = paramResolver.resolve(
                                            retrySupport.stripRetryFields(bdto.getConfig()), globalParams, previousResults);
                                    // 分支节点同样更新脚本日志归属
                                    ScriptLogContext.currentNode(bdto.getNodeCode(), bdto.getNodeName());
                                    NodeExecutionContext bnCtx = NodeExecutionContext.builder()
                                            .flowId(flowId).nodeId(null).nodeCode(bdto.getNodeCode())
                                            .nodeName(bdto.getNodeName()).nodeType(bdto.getNodeType())
                                            .config(bnConfig).rawConfig(bdto.getConfig())
                                            .globalParams(globalParams).previousResults(previousResults).build();
                                    NodeExecutor bnExec = nodeRegistry.get(bdto.getNodeType());
                                    NodeResult bnResult = retrySupport.executeWithRetry(bnExec, bnCtx, bdto.getConfig());
                                    long bnDur = System.currentTimeMillis() - bnStart;
                                    NodeResult bnTimed = NodeResult.of(bdto.getNodeCode(), bdto.getNodeName(), bdto.getNodeType(),
                                            bnResult.isSuccess(), bnResult.getData(), bnResult.getErrorMessage(), bnDur);
                                    previousResults.put(bdto.getNodeCode(), bnTimed);
                                    nodeResultList.add(bnTimed);
                                    if (!bnResult.isSuccess()) { success = false; errorMessage = "分支节点「" + bdto.getNodeName() + "」(" + bdto.getNodeCode() + ") 执行失败: " + bnResult.getErrorMessage(); break; }
                                    lastBranchResult = bnTimed;
                                } catch (Exception ex) { success = false; errorMessage = "分支节点「" + bdto.getNodeName() + "」(" + bdto.getNodeCode() + ") 执行异常: " + ex.getMessage(); break; }
                            }
                            if (!success || cancelled) break;
                            if (lastBranchResult != null) {
                                // 回写同一 timedResult 实例：previousResults 与 nodeResultList 同引用，两边同时生效
                                redisGetSetSupport.writeBackAndCache(dto.getConfig(), timedResult, lastBranchResult.getData(),
                                        globalParams, previousResults);
                            }
                        }
                    }
                }

                if (!result.isSuccess()) {
                    success = false;
                    errorMessage = "节点「" + name + "」(" + code + ") 执行失败: " + result.getErrorMessage();
                    log.warn("节点执行失败: code={}, error={}", code, result.getErrorMessage());
                    break;
                }

                // 主线节点执行成功后评估停止条件（分支节点不评估，START/END 无业务配置跳过）；
                // 表达式出错按流程失败处理，节点本身保持成功状态
                if (!"START".equals(type) && !"END".equals(type)) {
                    StopConditionSupport.StopResult stopResult;
                    try {
                        stopResult = stopConditionSupport.evaluate(dto.getConfig(), timedResult, globalParams, previousResults);
                    } catch (Exception se) {
                        success = false;
                        errorMessage = se.getMessage();
                        log.error("停止条件评估失败: code={}", code, se);
                        break;
                    }
                    if (stopResult != null) {
                        flowStopped = true;
                        stopMessage = stopResult.getMessage();
                        stopType = stopResult.getType();
                        log.info("流程停止: 节点 {} 触发{}停止条件, 原因={}", code,
                                StopConditionSupport.STOP_TYPE_NORMAL.equals(stopType) ? "正常" : "异常", stopResult.getMessage());
                        break;
                    }
                }
                log.info("节点执行成功: code={}", code);
            } catch (Exception e) {
                success = false;
                errorMessage = "节点「" + name + "」(" + code + ") 执行异常: " + e.getMessage();
                long failDuration = System.currentTimeMillis() - nodeStart;
                NodeResult failResult = NodeResult.of(code, name, type, false, null, errorMessage, failDuration);
                previousResults.put(code, failResult);
                nodeResultList.add(failResult);
                log.error("节点执行异常: code={}", code, e);
                break;
            }
        }

        // 停止条件触发/用户取消：剩余未执行的主线节点以 STOPPED 占位补入结果列表（前端执行列表展示全部节点）
        if (flowStopped || cancelled) {
            for (FlowSaveDTO.FlowNodeDTO dto : nodeDTOs) {
                if (dto.getBranchOfCode() != null && !dto.getBranchOfCode().isBlank()) continue;
                if (!previousResults.containsKey(dto.getNodeCode())) {
                    nodeResultList.add(NodeResult.stopped(dto.getNodeCode(), dto.getNodeName(), dto.getNodeType()));
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
        log.info("流程测试执行完成: id={}, status={}, duration={}ms", flowId, flowStatus, duration);

        // 本次执行捕获的脚本日志（节点循环已结束，此后不再有脚本执行）
        List<ScriptLogEntry> scriptLogs = scriptLogCollector.snapshot();

        // 保存执行日志（仅测试流程，应用执行由 ApplicationService 自行记录；短事务保证主日志与节点日志原子写入）
        if (writeLog) {
            final Flow persistFlow = flow;
            final String persistErrorMessage = flowStopped ? stopMessage : errorMessage;
            final String persistStopType = flowStopped ? stopType : null;
            TransactionTemplate tx = new TransactionTemplate(transactionManager);
            try {
                tx.executeWithoutResult(status -> {
                    com.arbor.entity.ExecutionLog execLog = new com.arbor.entity.ExecutionLog();
                    execLog.setFlowId(flowId);
                    execLog.setFlowName(persistFlow.getName());
                    execLog.setUserId(TenantContext.getUserId());
                    execLog.setExecuteType(FlowEngine.EXECUTE_MANUAL);
                    execLog.setSource(FlowEngine.SOURCE_FLOW_TEST);
                    execLog.setOwnerId(persistFlow.getUserId());
                    execLog.setExecutorId(TenantContext.getUserId());
                    execLog.setStatus(flowStatus);
                    execLog.setStartTime(startTime);
                    execLog.setEndTime(endTime);
                    execLog.setDuration(duration);
                    execLog.setErrorMessage(persistErrorMessage);
                    execLog.setStopType(persistStopType);
                    executionLogMapper.insert(execLog);

                    // 保存节点执行日志
                    int sortOrder = 0;
                    for (NodeResult nr : nodeResultList) {
                        com.arbor.entity.ExecutionNodeLog nodeLog = new com.arbor.entity.ExecutionNodeLog();
                        nodeLog.setExecutionLogId(execLog.getId());
                        nodeLog.setNodeCode(nr.getNodeCode());
                        nodeLog.setNodeName(nr.getNodeName());
                        nodeLog.setNodeType(nr.getNodeType());
                        nodeLog.setSortOrder(++sortOrder);
                        nodeLog.setStatus(nr.getStatus() != null ? nr.getStatus()
                                : (nr.isSuccess() ? NodeResult.STATUS_SUCCESS : NodeResult.STATUS_FAIL));
                        nodeLog.setDuration(nr.getDuration());
                        nodeLog.setErrorMessage(nr.getErrorMessage());
                        // 只保留子流程内部节点状态（历史详情要展示）；节点返回数据不再落库 ——
                        // 大返回值会撞 MySQL JSON 列的 max_allowed_packet，且详情已不再渲染它，
                        // 需要观察的数据由脚本自己 log 出来
                        if (nr.getSubFlowNodeDetails() != null) {
                            try {
                                Map<String, Object> wrapped = new LinkedHashMap<>();
                                wrapped.put("subFlowNodes", nr.getSubFlowNodeDetails());
                                nodeLog.setOutputResult(objectMapper.writeValueAsString(wrapped));
                            } catch (Exception ignored) {
                                nodeLog.setOutputResult(null);
                            }
                        } else {
                            nodeLog.setOutputResult(null);
                        }
                        executionNodeLogMapper.insert(nodeLog);
                    }

                    // 脚本日志与主日志/节点日志同事务，详情里三者要么都在要么都不在
                    executionScriptLogService.save(execLog.getId(), scriptLogs);
                });
            } catch (Exception e) {
                log.error("测试流程记录执行日志失败", e);
            }
        }

        // 清理流程级 class 缓存
        groovyScriptEngine.evictFlowClasses(flowId);

        // FILE 输出模式：检测 START 节点 DTO 的 outputType
        FlowExecutionResult.OutputFileInfo outputFile = null;
        if (success || flowStopped) {
            FlowSaveDTO.FlowNodeDTO startDto = nodeDTOs.stream()
                    .filter(d -> "START".equals(d.getNodeType()))
                    .findFirst().orElse(null);
            if (startDto != null) {
                try {
                    Map<String, Object> startCfg = objectMapper.readValue(startDto.getConfig(), Map.class);
                    outputFile = fileOutputHandler.handleFileOutput(
                            nodeResultList, startCfg, flowId, flow.getName());
                } catch (Exception e) {
                    log.error("FILE 输出模式: 解析 START DTO 配置失败", e);
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
                .executeType(FlowEngine.EXECUTE_MANUAL)
                .success(success)
                .status(flowStatus)
                .stopMessage(stopMessage)
                .stopType(stopType)
                .outputFile(outputFile)
                .startTime(startTime)
                .endTime(endTime)
                .duration(duration)
                .errorMessage(errorMessage)
                .nodeDetailList(nodeResultList)
                .scriptLogs(scriptLogs)
                .build();
    }

    /** 带 Redis 缓存的按 ID 查询流程（缓存 key 与 FlowService 保持一致） */
    private Flow getFlowCached(Long id) {
        Long userId = TenantContext.getUserId();
        String key = RedisCacheUtil.buildKey(FlowService.FLOW_CACHE_PREFIX, (userId != null ? userId : 0), "id", id);
        return redisCacheUtil.get(key, Flow.class)
                .orElseGet(() -> {
                    Flow cached = flowMapper.selectById(id);
                    if (cached != null) {
                        redisCacheUtil.set(key, cached, RedisCacheUtil.randomizedTtl());
                    }
                    return cached;
                });
    }
}
