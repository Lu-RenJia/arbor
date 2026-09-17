package com.arbor.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 角色实体
 *
 * @author gwl
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class Role extends BaseEntity {

    /** 角色编码 */
    private String code;

    /** 角色名称 */
    private String name;

    /** 角色权限标识（如 admin） */
    private String roleKey;

    /** 备注 */
    private String remark;

    /** 排序 */
    private Integer roleSort;

    /** 数据范围：1全部 2自定义 3本部门 4本部门及以下 5本人 */
    private String dataScope;

    /** 状态：0正常 1停用 */
    private String status;

    /** 删除标记：0正常 2删除 */
    private String delFlag;
}
