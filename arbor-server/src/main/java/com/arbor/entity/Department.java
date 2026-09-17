package com.arbor.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 部门实体
 *
 * @author gwl
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class Department extends BaseEntity {

    /** 部门名称 */
    private String name;

    /** 上级部门ID，0=顶级 */
    private Long parentId;

    /** 祖级列表（逗号分隔的ID） */
    private String ancestors;

    /** 备注 */
    private String remark;

    /** 负责人 */
    private String leader;

    /** 联系电话 */
    private String phone;

    /** 邮箱 */
    private String email;

    /** 排序 */
    private Integer sortOrder;

    /** 状态：0正常 1停用 */
    private String status;

    /** 删除标记：0正常 2删除 */
    private String delFlag;
}
