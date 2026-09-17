package com.arbor.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 字段映射实体 — 流程结果展示时的字段中英文映射 + 字典值替换
 *
 * @author gwl
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class FieldMapping extends BaseEntity {

    /** 关联流程ID */
    private Long flowId;
    /** 英文字段名（对应JSON key） */
    private String fieldName;
    /** 中文字段名（展示列头） */
    private String fieldLabel;
    /** 字典值映射JSON */
    private String mappingJson;
    /** 排序 */
    private Integer sortOrder;
    /** 是否启用 */
    private Integer isEnabled;
    /** 备注 */
    private String remark;
}
