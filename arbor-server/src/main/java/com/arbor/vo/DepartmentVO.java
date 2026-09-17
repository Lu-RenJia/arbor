package com.arbor.vo;

import com.arbor.entity.Department;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 部门 VO
 *
 * @author gwl
 */
@Data
@Builder
public class DepartmentVO {

    private Long id;
    /** 部门名称 */
    private String name;
    /** 上级部门ID */
    private Long parentId;
    /** 上级部门名称（JOIN填充） */
    private String parentName;
    /** 祖级列表 */
    private String ancestors;
    /** 备注 */
    private String remark;
    /** 负责人 */
    private String leader;
    /** 联系电话 */
    private String phone;
    /** 邮箱 */
    private String email;
    /** 排序号 */
    private Integer sortOrder;
    /** 状态 */
    private String status;
    /** 子部门列表（树形结构） */
    private List<DepartmentVO> children;
    /** 创建时间 */
    private LocalDateTime createTime;
    /** 更新时间 */
    private LocalDateTime updateTime;

    public static DepartmentVO fromEntity(Department entity, String parentName) {
        return DepartmentVO.builder()
                .id(entity.getId())
                .name(entity.getName())
                .parentId(entity.getParentId())
                .parentName(parentName)
                .ancestors(entity.getAncestors())
                .remark(entity.getRemark())
                .leader(entity.getLeader())
                .phone(entity.getPhone())
                .email(entity.getEmail())
                .sortOrder(entity.getSortOrder())
                .status(entity.getStatus())
                .createTime(entity.getCreateTime())
                .updateTime(entity.getUpdateTime())
                .build();
    }
}
