package com.arbor.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ApplicationDTO {

    /** 应用名称 */
    @NotBlank(message = "应用名称不能为空")
    private String name;

    /** 关联的流程ID */
    @NotNull(message = "请选择工作流")
    private Long flowId;

    /** 执行环境ID，NULL=默认环境 */
    private Long envId;

    /** 默认入参（JSON字符串或纯文本） */
    private String inputParams;

    /** 备注 */
    private String remark;
}
