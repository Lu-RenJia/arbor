package com.arbor.engine.executor;

import com.arbor.engine.NodeExecutionContext;
import com.arbor.engine.NodeExecutor;
import com.arbor.engine.NodeResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 结束节点执行器 — 流程出口标记，收集全部上游结果
 *
 * @author gwl
 */
@Slf4j
@Component
public class EndNodeExecutor implements NodeExecutor {

    /** 节点类型标识 */
    public static final String NODE_TYPE = "END";

    @Override
    public String getNodeType() {
        return NODE_TYPE;
    }

    @Override
    public NodeResult execute(NodeExecutionContext context) {
        log.info("END 节点: flowId={}, nodeCode={}, 上游节点数={}",
                context.getFlowId(), context.getNodeCode(),
                context.getPreviousResults() != null ? context.getPreviousResults().size() : 0);
        // 收集所有上游节点结果返回（快照副本：END 自身结果随后会被放回 previousResults，
        // 返回原引用会形成循环引用导致 JSON 序列化无限递归）
        return NodeResult.ok(new java.util.LinkedHashMap<>(context.getPreviousResults()));
    }
}
