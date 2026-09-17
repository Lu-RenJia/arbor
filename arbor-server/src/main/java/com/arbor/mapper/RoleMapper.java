package com.arbor.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.arbor.entity.Role;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 角色数据访问层
 *
 * @author gwl
 */
@Mapper
public interface RoleMapper {

    List<Role> selectAll();

    IPage<Role> selectPage(IPage<Role> page);

    Role selectById(Long id);

    Role selectByCode(String code);

    int insert(Role entity);

    int update(Role entity);

    int deleteById(Long id);
}
