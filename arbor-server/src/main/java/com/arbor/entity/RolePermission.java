package com.arbor.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 角色-权限关联实体
 *
 * @author gwl
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class RolePermission extends BaseEntity {

    /** 角色ID */
    private Long roleId;

    /** 权限ID */
    private Long permissionId;
}
