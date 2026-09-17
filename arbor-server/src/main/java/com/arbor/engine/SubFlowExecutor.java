package com.arbor.engine;

import com.arbor.common.AssertUtils;
import com.arbor.common.ErrorCode;
import com.arbor.common.EnvironmentContext;
import com.arbor.engine.executor.StartNodeExecutor;
import com.arbor.entity.FlowNode;
import com.arbor.groovy.log.ScriptLogContext;
import com.arbor.mapper.FlowMapper;
import com.arbor.mapper.FlowNodeMapper;
import com.arbor.service.GlobalParamService;
import com.arbor.util.RedisCacheUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 子流程执行器
 * 给定 flowId + inputParams + globalParams + depth，加载子流程节点并顺序执行，返回最终结果。
 * 不单独写 execution_log，由父流程的 execution_node_log 记录。
 *
 * @author gwl
 */
@Slf4j
@Component
public class SubFlowExecutor {

    /** 最大嵌套深度，超过此值判定为循环引用，拒绝执行 */
    public static final int MAX_DEPTH = 5;

    /** 子流程节点列表缓存 key 前缀（拼 flowId 后作为完整 key） */
    public static final String FLOW_NODES_CACHE_PREFIX = "flow_nodes:flowId";

    private final FlowMapper flowMapper;
    private final FlowNodeMapper flowNodeMapper;
    private final NodeRegistry nodeRegistry;
    private final ParamResolver paramResolver;
    private final RedisCacheUtil redisCacheUtil;
    private final GlobalParamService globalParamService;
    private final RedisGetSetSupport redisGetSetSupport;

    /**
     * 构造函数（使用 @Lazy 打破 NodeRegistry 循环依赖）
     */
    public SubFlowExecutor(FlowMapper flowMapper, FlowNodeMapper flowNodeMapper,
                           @Lazy NodeRegistry nodeRegistry, ParamResolver paramResolver,
                           RedisCacheUtil redisCacheUtil, GlobalParamService globalParamService,
                           RedisGetSetSupport redisGetSetSupport) {
        this.flowMapper = flowMapper;
        this.flowNodeMapper = flowNodeMapper;
        this.nodeRegistry = nodeRegistry;
        this.paramResolver = paramResolver;
        this.redisCacheUtil = redisCacheUtil;
        this.globalParamService = globalParamService;
        this.redisGetSetSupport = redisGetSetSupport;
    }

    /**
     * 执行子流程
     *
     * @param flowId       子流程ID
     * @param inputParams  传入子流程的入参（作为 flow_param）
     * @param globalParams 全局参数（继承父流程）
     * @param depth        当前嵌套深度（父流程传入，逐层+1）
     * @return 子流程最后一个成功节点的执行结果
     */
    /**
     * 执行子流程：加载节点列表 → 顺序执行 → 返回最后一个业务节点的输出。
     * 不单独写 execution_log，由父流程在 execution_node_log 中体现。
     *
     * @param flowId       子流程ID
     * @param inputParams  传入子流程的入参（作为 flow_param）
     * @param globalParams 全局参数（继承父流程的解析结果）
     * @param depth        当前嵌套深度（父流程传入，逐层+1）
     * @return 子流程最后一个成功节点的执行结果
     */
    public NodeResult execute(Long flowId, Object inputParams, Map<String, String> globalParams, int depth) {
        // 1. 深度校验
        if (depth > MAX_DEPTH) {
            return NodeResult.fail("子流程嵌套深度超过限制(" + MAX_DEPTH + "层)，请检查是否存在循环引用");
        }

        // 2. 校验子流程存在
        var flow = flowMapper.selectById(flowId);
        AssertUtils.notNull(flow, ErrorCode.FLOW_NOT_FOUND);

        // 3. 加载子流程节点（带缓存），分离主线/分支节点
        List<FlowNode> allNodes = getFlowNodesCached(flowId);
        List<FlowNode> nodes = allNodes.stream()
                .filter(n -> n.getBranchOf() == null)
                .collect(Collectors.toList());
        Map<Long, List<FlowNode>> branchMap = allNodes.stream()
                .filter(n -> n.getBranchOf() != null)
                .collect(Collectors.groupingBy(FlowNode::getBranchOf));
        if (nodes.isEmpty()) {
            return NodeResult.fail("子流程「" + flow.getName() + "」没有可执行节点");
        }

        log.info("子流程执行开始: flowId={}, name={}, depth={}, 节点数={}, envId={}",
                flowId, flow.getName(), depth, nodes.size(), EnvironmentContext.getEnvId());

        // 4. 基于当前 EnvironmentContext 重新加载全局参数（确保子流程使用正确环境的参数值，
        //    而非完全依赖父流程传入的 map，避免缓存时序差异导致环境参数不一致）
        Map<String, String> effectiveParams = globalParamService.getAllParams();
        if (globalParams != null) {
            // 父流程传入的参数作为补充（不覆盖当前环境的最新值）
            globalParams.forEach(effectiveParams::putIfAbsent);
        }

        // 5. 构建子流程的 previousResults，放入 flow_param
        Map<String, NodeResult> previousResults = new LinkedHashMap<>();
        if (inputParams != null) {
            NodeResult inputResult = NodeResult.of(
                    StartNodeExecutor.FLOW_PARAM_KEY, "入参", "PARAM",
                    true, inputParams, null, 0);
            previousResults.put(StartNodeExecutor.FLOW_PARAM_KEY, inputResult);
        }

        // 6. 顺序执行各节点
        Object lastData = null;
        List<Map<String, Object>> nodeDetails = new ArrayList<>();
        for (FlowNode node : nodes) {
            String nodeType = node.getNodeType();

            // START/END 结构节点：START 用 inputParams 作为结果，END 跳过
            if ("START".equals(nodeType)) {
                if (inputParams != null) {
                    NodeResult startResult = NodeResult.of(
                            node.getNodeCode(), node.getNodeName(), nodeType,
                            true, inputParams, null, 0);
                    previousResults.put(node.getNodeCode(), startResult);
                }
                continue;
            }
            if ("END".equals(nodeType)) {
                continue;
            }

            long nodeStart = System.currentTimeMillis();
            try {
                // 解析节点配置中的 ${} 参数
                String resolvedConfig = paramResolver.resolve(node.getConfig(), effectiveParams, previousResults);

                // 更新脚本日志归属到子流程内的当前节点（复用父级 ThreadLocal 上下文，日志自然冒泡到父流程）
                ScriptLogContext.currentNode(node.getNodeCode(), node.getNodeName());

                // 构建执行上下文
                NodeExecutionContext context = NodeExecutionContext.builder()
                        .flowId(flowId)
                        .nodeId(node.getId())
                        .nodeCode(node.getNodeCode())
                        .nodeName(node.getNodeName())
                        .nodeType(nodeType)
                        .config(resolvedConfig)
                        .rawConfig(node.getConfig())
                        .globalParams(effectiveParams)
                        .previousResults(previousResults)
                        .depth(depth)
                        .build();

                // 获取执行器并执行
                NodeExecutor executor = nodeRegistry.get(nodeType);
                NodeResult result = executor.execute(context);

                long nodeDuration = System.currentTimeMillis() - nodeStart;
                NodeResult timedResult = NodeResult.of(
                        node.getNodeCode(), node.getNodeName(), nodeType,
                        result.isSuccess(), result.getData(), result.getErrorMessage(), nodeDuration);
                previousResults.put(node.getNodeCode(), timedResult);

                // 收集节点明细（供父流程日志展示）
                Map<String, Object> detail = new LinkedHashMap<>();
                detail.put("nodeName", node.getNodeName());
                detail.put("nodeCode", node.getNodeCode());
                detail.put("nodeType", nodeType);
                detail.put("status", result.isSuccess() ? "SUCCESS" : "FAIL");
                detail.put("duration", nodeDuration);
                detail.put("errorMessage", result.getErrorMessage());
                nodeDetails.add(detail);

                if (!result.isSuccess()) {
                    log.warn("子流程节点执行失败: flowId={}, nodeCode={}, error={}",
                            flowId, node.getNodeCode(), result.getErrorMessage());
                    return NodeResult.fail("子流程「" + flow.getName() + "」节点「"
                            + node.getNodeName() + "」执行失败: " + result.getErrorMessage());
                }

                // REDIS GET&SET 缓存未命中 → 执行分支流程并回写（与 FlowEngine 行为一致）
                if ("REDIS".equals(nodeType) && result.getData() == null
                        && redisGetSetSupport.isGetSetNode(node.getConfig())) {
                    List<FlowNode> branches = branchMap.get(node.getId());
                    if (branches != null && !branches.isEmpty()) {
                        if (redisGetSetSupport.getSetTtlSeconds(node.getConfig()) <= 0) {
                            return NodeResult.fail("子流程「" + flow.getName() + "」Redis 节点「"
                                    + node.getNodeName() + "」的 GET&SET 配置未设置有效过期时间");
                        }
                        log.info("子流程 Redis GET&SET 未命中，执行分支: flowId={}, 分支节点数={}", flowId, branches.size());
                        NodeResult lastBranchResult = null;
                        for (FlowNode bn : branches) {
                            long bnStart = System.currentTimeMillis();
                            try {
                                String bnConfig = paramResolver.resolve(bn.getConfig(), effectiveParams, previousResults);
                                // 分支节点同样更新脚本日志归属
                                ScriptLogContext.currentNode(bn.getNodeCode(), bn.getNodeName());
                                NodeExecutionContext bnCtx = NodeExecutionContext.builder()
                                        .flowId(flowId).nodeId(bn.getId()).nodeCode(bn.getNodeCode())
                                        .nodeName(bn.getNodeName()).nodeType(bn.getNodeType())
                                        .config(bnConfig).rawConfig(bn.getConfig())
                                        .globalParams(effectiveParams)
                                        .previousResults(previousResults).depth(depth).build();
                                NodeExecutor bnExec = nodeRegistry.get(bn.getNodeType());
                                NodeResult bnResult = bnExec.execute(bnCtx);
                                long bnDur = System.currentTimeMillis() - bnStart;
                                NodeResult bnTimed = NodeResult.of(bn.getNodeCode(), bn.getNodeName(), bn.getNodeType(),
                                        bnResult.isSuccess(), bnResult.getData(), bnResult.getErrorMessage(), bnDur);
                                previousResults.put(bn.getNodeCode(), bnTimed);
                                if (!bnResult.isSuccess()) {
                                    return NodeResult.fail("子流程「" + flow.getName() + "」分支节点「"
                                            + bn.getNodeName() + "」执行失败: " + bnResult.getErrorMessage());
                                }
                                lastBranchResult = bnTimed;
                            } catch (Exception ex) {
                                return NodeResult.fail("子流程「" + flow.getName() + "」分支节点「"
                                        + bn.getNodeName() + "」执行异常: " + ex.getMessage());
                            }
                        }
                        if (lastBranchResult != null) {
                            redisGetSetSupport.writeBackAndCache(node.getConfig(), timedResult,
                                    lastBranchResult.getData(), effectiveParams, previousResults);
                        }
                    }
                }

                lastData = timedResult.getData();
                log.debug("子流程节点执行成功: flowId={}, nodeCode={}, duration={}ms",
                        flowId, node.getNodeCode(), nodeDuration);

            } catch (Exception e) {
                log.error("子流程节点执行异常: flowId={}, nodeCode={}", flowId, node.getNodeCode(), e);
                return NodeResult.fail("子流程「" + flow.getName() + "」节点「"
                        + node.getNodeName() + "」执行异常: " + e.getMessage());
            }
        }

        log.info("子流程执行完成: flowId={}, name={}, depth={}", flowId, flow.getName(), depth);
        NodeResult finalResult = NodeResult.ok(lastData);
        finalResult.setSubFlowNodeDetails(nodeDetails);
        return finalResult;
    }

    /**
     * 带缓存的加载子流程节点列表
     *
     * @param flowId 流程ID
     * @return 节点列表（按 sort_order 排序）
     */
    /**
     * 带 Redis 缓存的加载子流程节点列表（按 sort_order 排序）。
     * 缓存于流程节点变更时由 {@code FlowService.saveNodes()} 和 {@code FlowService.delete()} 清除。
     *
     * @param flowId 流程ID
     * @return 节点列表（含分支节点），可能为空
     */
    private List<FlowNode> getFlowNodesCached(Long flowId) {
        String key = RedisCacheUtil.buildKey(FLOW_NODES_CACHE_PREFIX, flowId);
        return redisCacheUtil.get(key, new TypeReference<List<FlowNode>>() {})
                .orElseGet(() -> {
                    List<FlowNode> nodes = flowNodeMapper.selectByFlowId(flowId);
                    if (nodes != null && !nodes.isEmpty()) {
                        redisCacheUtil.set(key, nodes, RedisCacheUtil.randomizedTtl());
                    }
                    return nodes;
                });
    }
}
