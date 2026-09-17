package com.arbor.vo;

import com.arbor.entity.User;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户 VO（不含密码）
 *
 * @author gwl
 */
@Data
@Builder
public class UserVO {

    private Long id;
    /** 用户名 */
    private String username;
    /** 昵称 */
    private String nickname;
    /** 邮箱 */
    private String email;
    /** 手机号 */
    private String phone;
    /** 性别 */
    private String sex;
    /** 角色 */
    private String role;
    /** 部门ID */
    private Long departmentId;
    /** 部门名称（JOIN填充） */
    private String departmentName;
    /** 状态 */
    private String status;
    /** 创建时间 */
    private LocalDateTime createTime;
    /** 更新时间 */
    private LocalDateTime updateTime;

    public static UserVO fromEntity(User user, String departmentName) {
        return UserVO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .email(user.getEmail())
                .phone(user.getPhone())
                .sex(user.getSex())
                .role(user.getRole())
                .departmentId(user.getDepartmentId())
                .departmentName(departmentName)
                .status(user.getStatus())
                .createTime(user.getCreateTime())
                .updateTime(user.getUpdateTime())
                .build();
    }
}
