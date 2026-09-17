package com.arbor.engine;

import com.arbor.common.AssertUtils;
import com.arbor.common.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 节点执行器注册中心（工厂模式）
 * 管理所有 NodeExecutor 实现，根据 nodeType 获取对应执行器
 *
 * @author gwl
 */
@Slf4j
@Component
public class NodeRegistry {

    /** 执行器注册表 */
    private final Map<String, NodeExecutor> registry = new ConcurrentHashMap<>();

    /**
     * Spring 自动注入所有 NodeExecutor 实现
     */
    public NodeRegistry(List<NodeExecutor> executors) {
        for (NodeExecutor executor : executors) {
            String type = executor.getNodeType();
            registry.put(type, executor);
            log.info("注册节点执行器: {} -> {}", type, executor.getClass().getSimpleName());
        }
    }

    /**
     * 根据节点类型获取执行器
     *
     * @param nodeType 节点类型
     * @return 对应的 NodeExecutor
     */
    public NodeExecutor get(String nodeType) {
        NodeExecutor executor = registry.get(nodeType);
        AssertUtils.notNull(executor, ErrorCode.NODE_TYPE_UNSUPPORTED, nodeType);
        return executor;
    }
}
