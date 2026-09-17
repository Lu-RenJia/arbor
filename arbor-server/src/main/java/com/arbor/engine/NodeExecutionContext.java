package com.arbor.engine;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * 节点执行上下文
 * 包含当前节点配置、全局参数、前置节点结果等
 *
 * @author gwl
 */
@Data
@Builder
public class NodeExecutionContext {

    /** 当前流程ID */
    private Long flowId;

    /** 节点ID */
    private Long nodeId;

    /** 节点编码 */
    private String nodeCode;

    /** 节点名称 */
    private String nodeName;

    /** 节点类型 */
    private String nodeType;

    /** 节点配置（JSON字符串，已完成参数解析） */
    private String config;

    /** 原始节点配置（JSON字符串，未做参数解析，DB_QUERY 节点用于参数化 SQL） */
    private String rawConfig;

    /** 全局参数 Map */
    private Map<String, String> globalParams;

    /** 前置节点结果 Map（key=nodeCode, value=NodeResult） */
    private Map<String, NodeResult> previousResults;

    /** 嵌套深度（0=主线流程，子流程逐层+1，用于递归保护） */
    @Builder.Default
    private int depth = 0;
}
