package com.arbor.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 节点参数引用实体
 *
 * @author gwl
 */
@Data
public class FlowNodeParam {

    /** 主键ID */
    private Long id;

    /** 所属流程节点ID */
    private Long flowNodeId;

    /** 参数名 */
    private String paramName;

    /** 来源类型：GLOBAL/NODE */
    private String paramSourceType;

    /** 来源节点ID（来源类型为NODE时） */
    private Long sourceNodeId;

    /** 参数表达式 */
    private String paramExpression;

    /** 创建时间 */
    private LocalDateTime createTime;
}
