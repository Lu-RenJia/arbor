package com.arbor.vo;

import com.arbor.entity.Menu;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 菜单 VO（含树形子节点）
 *
 * @author gwl
 */
@Data
@Builder
public class MenuVO {

    private Long id;
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
    /** 子菜单列表（树形结构） */
    private List<MenuVO> children;
    /** 创建时间 */
    private LocalDateTime createTime;
    /** 更新时间 */
    private LocalDateTime updateTime;

    public static MenuVO fromEntity(Menu entity) {
        return MenuVO.builder()
                .id(entity.getId())
                .parentId(entity.getParentId())
                .name(entity.getName())
                .path(entity.getPath())
                .component(entity.getComponent())
                .query(entity.getQuery())
                .isFrame(entity.getIsFrame())
                .routeName(entity.getRouteName())
                .menuType(entity.getMenuType())
                .isCache(entity.getIsCache())
                .icon(entity.getIcon())
                .permissionCode(entity.getPermissionCode())
                .sortOrder(entity.getSortOrder())
                .visible(entity.getVisible())
                .status(entity.getStatus())
                .children(new ArrayList<>())
                .createTime(entity.getCreateTime())
                .updateTime(entity.getUpdateTime())
                .build();
    }
}
