package com.arbor.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 登录响应VO
 *
 * @author gwl
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginVO {

    /** 用户ID（前端按 sharedBy 门控「移除共享」等按钮时需要） */
    private Long id;

    /** JWT Token */
    private String token;

    /** 用户名 */
    private String username;

    /** 昵称 */
    private String nickname;

    /** 角色：ADMIN / USER */
    private String role;

    /** 权限码列表 */
    private List<String> permissions;
}
