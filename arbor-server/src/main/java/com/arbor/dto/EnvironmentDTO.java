package com.arbor.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 环境配置 DTO
 *
 * @author gwl
 */
@Data
public class EnvironmentDTO {

    /** 环境名称 */
    @NotBlank(message = "环境名称不能为空")
    private String name;

    /** 环境代码（字母开头，仅字母数字下划线连字符） */
    @NotBlank(message = "环境代码不能为空")
    @Pattern(regexp = "[a-zA-Z][a-zA-Z0-9_-]*", message = "环境代码必须以字母开头，仅含字母数字下划线和连字符")
    private String code;

    /** 备注 */
    private String remark;

    /** 排序号 */
    private Integer sortOrder;
}
