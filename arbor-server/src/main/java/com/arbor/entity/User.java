package com.arbor.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 用户实体
 *
 * @author gwl
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class User extends BaseEntity {

    /** 用户名 */
    private String username;

    /** 密码（BCrypt加密） */
    private String password;

    /** 昵称 */
    private String nickname;

    /** 邮箱 */
    private String email;

    /** 手机号 */
    private String phone;

    /** 性别：0男 1女 2未知 */
    private String sex;

    /** 头像地址 */
    private String avatar;

    /** 角色：ADMIN / USER（向后兼容，优先使用 user_role 多对多） */
    private String role;

    /** 部门ID */
    private Long departmentId;

    /** 状态：0正常 1停用 */
    private String status;

    /** 最后登录IP */
    private String loginIp;

    /** 最后登录时间 */
    private LocalDateTime loginDate;

    /** 密码最后修改时间 */
    private LocalDateTime pwdUpdateDate;

    /** 删除标记：0正常 2删除 */
    private String delFlag;

    /** 创建者 */
    private String createBy;

    /** 更新者 */
    private String updateBy;
}
