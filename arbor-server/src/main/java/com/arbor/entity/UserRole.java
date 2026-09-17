package com.arbor.entity;

import lombok.Data;

/**
 * 用户-角色关联实体
 *
 * @author gwl
 */
@Data
public class UserRole {

    /** 用户ID */
    private Long userId;

    /** 角色ID */
    private Long roleId;
}
