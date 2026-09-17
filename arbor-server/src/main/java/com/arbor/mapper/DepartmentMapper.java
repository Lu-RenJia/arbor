package com.arbor.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.arbor.entity.Department;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 部门数据访问层
 *
 * @author gwl
 */
@Mapper
public interface DepartmentMapper {

    List<Department> selectAll();

    IPage<Department> selectPage(IPage<Department> page);

    Department selectById(Long id);

    int insert(Department entity);

    int update(Department entity);

    int deleteById(Long id);
}
