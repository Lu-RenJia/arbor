package com.arbor.vo;

import com.arbor.entity.FlowVariable;
import lombok.Builder;
import lombok.Data;

/**
 * 流程变量 VO（含实时解析的全局参数值、备注和节点信息）
 *
 * @author gwl
 */
@Data
@Builder
public class FlowVariableVO {

    private Long id;
    /** 流程ID */
    private Long flowId;
    /** 变量名 */
    private String varName;
    /** 关联全局参数ID */
    private Long paramId;
    /** 关联节点ID（旧方式） */
    private Long nodeId;
    /** 关联数据源ID */
    private Long datasourceId;

    /** 以下为实时解析字段（仅全局参数类型有值） */
    /** 参数值（当前环境） */
    private String paramValue;
    /** 参数名 */
    private String paramName;
    /** 参数备注 */
    private String paramRemark;

    /** 节点信息（仅节点类型有值） */
    /** 节点编码 */
    private String nodeCode;
    /** 节点名称 */
    private String nodeName;

    /** 数据源信息（仅数据源类型有值） */
    /** 数据源名称 */
    private String datasourceName;

    public static FlowVariableVO fromEntity(FlowVariable entity) {
        return FlowVariableVO.builder()
                .id(entity.getId())
                .flowId(entity.getFlowId())
                .varName(entity.getVarName())
                .paramId(entity.getParamId())
                .nodeId(entity.getNodeId())
                .nodeCode(entity.getNodeCode())
                .datasourceId(entity.getDatasourceId())
                .build();
    }
}
