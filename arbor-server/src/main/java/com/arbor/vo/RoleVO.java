package com.arbor.vo;

import com.arbor.entity.Role;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 角色 VO
 *
 * @author gwl
 */
@Data
@Builder
public class RoleVO {

    private Long id;
    /** 角色编码 */
    private String code;
    /** 角色权限标识 */
    private String roleKey;
    /** 角色名称 */
    private String name;
    /** 备注 */
    private String remark;
    /** 排序号 */
    private Integer roleSort;
    /** 状态 */
    private String status;
    /** 权限ID列表 */
    private List<Long> permissionIds;
    /** 权限列表（详情接口返回） */
    private List<PermissionVO> permissions;
    /** 创建时间 */
    private LocalDateTime createTime;
    /** 更新时间 */
    private LocalDateTime updateTime;

    public static RoleVO fromEntity(Role entity) {
        return RoleVO.builder()
                .id(entity.getId())
                .code(entity.getCode())
                .roleKey(entity.getRoleKey())
                .name(entity.getName())
                .remark(entity.getRemark())
                .roleSort(entity.getRoleSort())
                .status(entity.getStatus())
                .createTime(entity.getCreateTime())
                .updateTime(entity.getUpdateTime())
                .build();
    }
}
