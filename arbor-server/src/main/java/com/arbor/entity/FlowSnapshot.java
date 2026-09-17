package com.arbor.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class FlowSnapshot {
    private Long id;
    /** 流程ID */
    private Long flowId;
    /** 快照版本号 */
    private Integer version;
    /** 流程名称（快照时的值） */
    private String name;
    /** 流程描述（快照时的值） */
    private String description;
    /** 状态: 0=关闭, 1=启用 */
    private Integer status;
    /** 节点数量（快照时的值） */
    private Integer nodeCount;
    /** 快照创建时间 */
    private LocalDateTime snapshotTime;
}
