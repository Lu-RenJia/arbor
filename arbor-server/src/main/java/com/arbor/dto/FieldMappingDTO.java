package com.arbor.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 字段映射 DTO
 *
 * @author gwl
 */
@Data
public class FieldMappingDTO {

    /** 由 Controller 从 URL 注入 */
    private Long flowId;

    /** 英文字段名 */
    @NotBlank(message = "英文字段名不能为空")
    private String fieldName;

    /** 中文显示名 */
    @NotBlank(message = "中文字段名不能为空")
    private String fieldLabel;

    /** 字典值映射 JSON */
    private String mappingJson;

    /** 排序 */
    private Integer sortOrder;

    /** 是否启用 */
    private Integer isEnabled;

    /** 备注 */
    private String remark;
}
