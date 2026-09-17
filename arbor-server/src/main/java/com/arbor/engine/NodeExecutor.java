package com.arbor.engine;

/**
 * 节点执行器接口（策略模式）
 * 每种节点类型对应一个实现类
 *
 * @author gwl
 */
public interface NodeExecutor {

    /**
     * 执行节点
     *
     * @param context 节点执行上下文
     * @return 执行结果
     */
    NodeResult execute(NodeExecutionContext context);

    /**
     * 获取支持的节点类型
     *
     * @return 节点类型标识
     */
    String getNodeType();
}
