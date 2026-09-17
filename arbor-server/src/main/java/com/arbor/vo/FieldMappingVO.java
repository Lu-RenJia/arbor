package com.arbor.vo;

import com.arbor.entity.FieldMapping;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 字段映射 VO
 *
 * @author gwl
 */
@Data
@Builder
public class FieldMappingVO {

    private Long id;
    /** 流程ID */
    private Long flowId;
    /** 英文字段名 */
    private String fieldName;
    /** 中文显示名 */
    private String fieldLabel;
    /** 字典值映射 JSON */
    private String mappingJson;
    /** 排序号 */
    private Integer sortOrder;
    /** 是否启用 */
    private Integer isEnabled;
    /** 备注 */
    private String remark;
    /** 创建时间 */
    private LocalDateTime createTime;
    /** 更新时间 */
    private LocalDateTime updateTime;

    public static FieldMappingVO fromEntity(FieldMapping entity) {
        return FieldMappingVO.builder()
                .id(entity.getId())
                .flowId(entity.getFlowId())
                .fieldName(entity.getFieldName())
                .fieldLabel(entity.getFieldLabel())
                .mappingJson(entity.getMappingJson())
                .sortOrder(entity.getSortOrder())
                .isEnabled(entity.getIsEnabled())
                .remark(entity.getRemark())
                .createTime(entity.getCreateTime())
                .updateTime(entity.getUpdateTime())
                .build();
    }
}
