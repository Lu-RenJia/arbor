package com.arbor.security;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 登录用户信息 — 存 Redis，每次请求从中读取权限和租户信息，无需查 DB
 *
 * @author gwl
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginUser {

    /** 令牌 UUID */
    private String token;

    /** 用户 ID */
    private Long userId;

    /** 用户名 */
    private String username;

    /** 昵称 */
    private String nickname;

    /** 主角色 */
    private String role;

    /** 权限码列表 */
    private List<String> permissions;

    /** 登录时间（epoch millis） */
    private long loginTime;

    /** 过期时间（epoch millis） */
    private long expireTime;
}
