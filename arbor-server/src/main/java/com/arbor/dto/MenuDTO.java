package com.arbor.dto;

import lombok.Data;

/**
 * 菜单 DTO
 *
 * @author gwl
 */
@Data
public class MenuDTO {

    /** 上级菜单ID */
    private Long parentId;
    /** 菜单名称 */
    private String name;
    /** 路由路径 */
    private String path;
    /** 组件路径 */
    private String component;
    /** 路由参数 */
    private String query;
    /** 是否为外链 */
    private String isFrame;
    /** 路由名称 */
    private String routeName;
    /** 菜单类型 */
    private String menuType;
    /** 是否缓存 */
    private String isCache;
    /** 图标 */
    private String icon;
    /** 权限标识 */
    private String permissionCode;
    /** 排序号 */
    private Integer sortOrder;
    /** 是否可见 */
    private Integer visible;
    /** 状态 */
    private String status;
}
