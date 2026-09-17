package com.arbor.mapper;

import com.arbor.entity.Menu;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 菜单数据访问层
 *
 * @author gwl
 */
@Mapper
public interface MenuMapper {

    List<Menu> selectAll();

    Menu selectById(Long id);

    int insert(Menu entity);

    int update(Menu entity);

    int deleteById(Long id);

    /** 查某用户可见的菜单（按权限码匹配） */
    List<Menu> selectByPermissionCodes(@Param("codes") List<String> permissionCodes);

    /** 查某角色的菜单权限码列表 */
    List<String> selectPermissionCodesByRoleId(@Param("roleId") Long roleId);

    /** 统计子菜单数量 */
    int countByParentId(@Param("parentId") Long parentId);

    /** 统计权限码被角色引用的次数 */
    int countRoleRefByPermissionCode(@Param("permissionCode") String permissionCode);
}
