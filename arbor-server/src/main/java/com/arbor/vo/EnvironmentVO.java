package com.arbor.vo;

import com.arbor.entity.Environment;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 环境配置 VO（列表展示）
 *
 * @author gwl
 */
@Data
@Builder
public class EnvironmentVO {

    private Long id;
    /** 环境名称 */
    private String name;

    /** 环境代码 */
    private String code;

    /** 备注 */
    private String remark;

    /** 是否默认环境 */
    private Integer isDefault;

    /** 是否已删除 */
    private Integer isDeleted;

    /** 删除时间 */
    private LocalDateTime deletedTime;

    /** 排序 */
    private Integer sortOrder;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 修改时间 */
    private LocalDateTime updateTime;

    public static EnvironmentVO fromEntity(Environment entity) {
        return EnvironmentVO.builder()
                .id(entity.getId())
                .name(entity.getName())
                .code(entity.getCode())
                .remark(entity.getRemark())
                .isDefault(entity.getIsDefault())
                .isDeleted(entity.getIsDeleted())
                .deletedTime(entity.getDeletedTime())
                .sortOrder(entity.getSortOrder())
                .createTime(entity.getCreateTime())
                .updateTime(entity.getUpdateTime())
                .build();
    }
}
