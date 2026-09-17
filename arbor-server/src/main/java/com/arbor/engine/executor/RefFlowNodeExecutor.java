package com.arbor.engine.executor;

import com.arbor.engine.NodeExecutionContext;
import com.arbor.engine.NodeExecutor;
import com.arbor.engine.NodeResult;
import com.arbor.engine.SubFlowExecutor;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 引用流程节点执行器
 * 将一个已保存的流程作为节点执行，支持入参传递，子流程最后一个节点的输出作为本节点结果。
 *
 * @author gwl
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RefFlowNodeExecutor implements NodeExecutor {

    /** 节点类型标识 */
    public static final String NODE_TYPE = "REF_FLOW";

    private final SubFlowExecutor subFlowExecutor;
    private final ObjectMapper objectMapper;

    @Override
    public String getNodeType() {
        return NODE_TYPE;
    }

    /**
     * 执行引用流程节点：从 config 中读取目标 flowId 和可选入参，
     * 委托 {@link SubFlowExecutor} 执行子流程，嵌套深度 +1。
     * <p>子流程最后一个业务节点的输出作为本节点结果返回。
     *
     * @param context 节点执行上下文（含已解析的 config、全局参数、前置结果、当前深度）
     * @return 子流程最终输出，或失败信息
     */
    @Override
    public NodeResult execute(NodeExecutionContext context) {
        try {
            Map<String, Object> configMap = objectMapper.readValue(context.getConfig(), Map.class);
            Long flowId = toLong(configMap.get("flowId"));

            if (flowId == null) {
                return NodeResult.fail("引用流程配置不完整：缺少 flowId");
            }

            // 入参（可选）：config 中的 inputParams 已被 ParamResolver 解析过
            Object inputParams = configMap.get("inputParams");

            log.info("REF_FLOW 执行: subFlowId={}, nodeCode={}, depth={}",
                    flowId, context.getNodeCode(), context.getDepth());

            // 调用子流程执行器，深度+1（当前节点所属的父流程深度 +1 = 子流程深度）
            return subFlowExecutor.execute(flowId, inputParams, context.getGlobalParams(), context.getDepth() + 1);

        } catch (Exception e) {
            log.error("REF_FLOW 执行失败", e);
            return NodeResult.fail("子流程执行失败: " + e.getMessage());
        }
    }

    /** 安全类型转换：Number/String → Long，无法转换返回 null */
    private Long toLong(Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value instanceof String) {
            try {
                return Long.parseLong((String) value);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }
}
