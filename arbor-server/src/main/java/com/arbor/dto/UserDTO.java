package com.arbor.dto;

import lombok.Data;

/**
 * 用户 DTO
 *
 * @author gwl
 */
@Data
public class UserDTO {

    /** 用户名（创建时必填，编辑时可不传） */
    private String username;

    /** 密码（创建/重置密码时必填，编辑时可不传） */
    private String password;

    /** 昵称 */
    private String nickname;

    /** 角色 */
    private String role;

    /** 部门ID */
    private Long departmentId;

    /** 状态 */
    private String status;
}
