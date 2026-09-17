package com.arbor.mapper;

import com.arbor.entity.RolePermission;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 角色-权限关联数据访问层
 *
 * @author gwl
 */
@Mapper
public interface RolePermissionMapper {

    /** 批量插入 */
    int batchInsert(@Param("roleId") Long roleId, @Param("permissionIds") List<Long> permissionIds);

    /** 删除某角色的所有权限关联 */
    int deleteByRoleId(@Param("roleId") Long roleId);

    /** 查询某角色的权限ID列表 */
    List<Long> selectPermissionIdsByRoleId(@Param("roleId") Long roleId);
}
