package com.arbor.mapper;

import com.arbor.entity.UserRole;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 用户-角色关联数据访问层
 *
 * @author gwl
 */
@Mapper
public interface UserRoleMapper {

    /** 根据用户ID查询角色ID列表 */
    List<Long> selectRoleIdsByUserId(Long userId);

    /** 批量插入用户-角色关联 */
    int batchInsert(@Param("userId") Long userId, @Param("roleIds") List<Long> roleIds);

    /** 删除用户的所有角色关联 */
    int deleteByUserId(Long userId);

    /** 批量查询用户ID对应的角色编码 */
    List<String> selectRoleCodesByUserId(Long userId);

    /** 查询拥有某角色的所有用户ID（角色权限变更时刷新缓存用） */
    List<Long> selectUserIdsByRoleId(Long roleId);
}
