package com.arbor.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 流程节点定义实体
 *
 * @author gwl
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class FlowNode extends BaseEntity {

    /** 所属流程ID */
    private Long flowId;

    /** 节点编码（流程内唯一） */
    private String nodeCode;

    /** 节点名称 */
    private String nodeName;

    /** 节点类型：DB_QUERY/GROOVY_SCRIPT/API_SEND/REF_DB_SCRIPT/REF_PROGRAM_SCRIPT/REF_API */
    private String nodeType;

    /** 执行顺序 */
    private Integer sortOrder;

    /** 节点配置JSON */
    private String config;

    /** 示例输出结果（JSON） */
    private String exampleOutput;

    /** 所属分支父节点ID（null=主线节点） */
    private Long branchOf;
}
