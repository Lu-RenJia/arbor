package com.arbor.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 流程变量 DTO
 *
 * @author gwl
 */
@Data
public class FlowVariableDTO {

    /** 由 Controller 从 URL 注入 */
    private Long flowId;

    /** 变量名（在 Groovy 脚本中通过此名引用） */
    @NotBlank(message = "变量名不能为空")
    private String varName;

    /** 关联全局参数ID（与 nodeId 二选一） */
    private Long paramId;

    /** 关联节点ID（与 paramId/datasourceId 三选一，旧方式） */
    private Long nodeId;
    /** 关联节点编码（与 paramId/datasourceId 三选一，推荐，稳定标识） */
    private String nodeCode;
    /** 关联数据源ID（与 paramId/nodeId 三选一） */
    private Long datasourceId;
}
