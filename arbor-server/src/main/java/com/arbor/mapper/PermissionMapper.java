package com.arbor.mapper;

import com.arbor.entity.Permission;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 权限数据访问层
 *
 * @author gwl
 */
@Mapper
public interface PermissionMapper {

    List<Permission> selectAll();

    /** 查某角色拥有的权限列表 */
    List<Permission> selectByRoleId(@Param("roleId") Long roleId);

    /** 查某角色拥有的权限码列表 */
    List<String> selectCodesByRoleCode(@Param("roleCode") String roleCode);
}
