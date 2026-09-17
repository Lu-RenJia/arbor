package com.arbor.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 权限实体
 *
 * @author gwl
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class Permission extends BaseEntity {

    /** 权限码 */
    private String code;

    /** 权限名称 */
    private String name;

    /** 备注 */
    private String remark;
}
