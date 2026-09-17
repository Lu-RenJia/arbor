package com.arbor.entity;

import lombok.Data;

@Data
public class FlowNodeSnapshot {
    private Long id;
    /** 关联的快照ID */
    private Long snapshotId;
    /** 原始节点ID */
    private Long nodeId;
    /** 流程ID */
    private Long flowId;
    /** 节点编码 */
    private String nodeCode;
    /** 节点名称 */
    private String nodeName;
    /** 节点类型 */
    private String nodeType;
    /** 排序号 */
    private Integer sortOrder;
    /** 节点配置（JSON字符串，快照时的值） */
    private String config;
    /** 分支归属节点ID（非分支节点为 null） */
    private Long branchOf;
}
