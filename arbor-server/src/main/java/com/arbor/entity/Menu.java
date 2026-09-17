package com.arbor.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 菜单实体
 *
 * @author gwl
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class Menu extends BaseEntity {

    /** 父菜单ID，0=顶级 */
    private Long parentId;

    /** 菜单名称 */
    private String name;

    /** 路由路径 */
    private String path;

    /** 组件路径 */
    private String component;

    /** 路由参数 */
    private String query;

    /** 是否外链：0否 1是 */
    private String isFrame;

    /** 路由名称（Vue3 去重用） */
    private String routeName;

    /** 菜单类型：M目录 C菜单 F按钮 */
    private String menuType;

    /** 是否缓存：0否 1是 */
    private String isCache;

    /** 图标 */
    private String icon;

    /** 关联权限码 */
    private String permissionCode;

    /** 排序 */
    private Integer sortOrder;

    /** 是否可见 */
    private Integer visible;

    /** 状态：0正常 1停用 */
    private String status;

    /** 删除标记：0正常 2删除 */
    private String delFlag;
}
