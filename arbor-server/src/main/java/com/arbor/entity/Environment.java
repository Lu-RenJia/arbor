package com.arbor.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 环境配置实体
 *
 * @author gwl
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class Environment extends BaseEntity {

    /** 环境名称 */
    private String name;

    /** 环境代码（唯一） */
    private String code;

    /** 备注 */
    private String remark;

    /** 是否默认环境：1=是 0=否 */
    private Integer isDefault;

    /** 软删除标志：1=已删除 0=正常 */
    private Integer isDeleted;

    /** 删除时间 */
    private LocalDateTime deletedTime;

    /** 排序 */
    private Integer sortOrder;
}
