package com.arbor.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 流程变量池实体
 *
 * @author gwl
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class FlowVariable extends BaseEntity {

    /** 所属流程ID */
    private Long flowId;
    /** 变量别名（脚本中使用的名字） */
    private String varName;
    /** 关联全局参数ID */
    private Long paramId;
    /** 关联节点ID（旧，将会废弃，node_code 稳定后不再需要） */
    private Long nodeId;
    /** 关联节点编码（稳定标识，替代 nodeId） */
    private String nodeCode;
    /** 关联数据源ID（通过数据源配置执行SQL） */
    private Long datasourceId;
}
