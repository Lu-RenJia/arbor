package com.arbor.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.arbor.entity.Application;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Set;

@Mapper
public interface ApplicationMapper {

    IPage<Application> selectPage(IPage<Application> page, @Param("name") String name, @Param("flowName") String flowName);

    /** 根据ID查询（绕过租户过滤，广场共享的应用可能属于其他用户） */
    @InterceptorIgnore(tenantLine = "true")
    Application selectById(Long id);

    /** 批量查（绕过租户过滤，用于广场应用列表） */
    @InterceptorIgnore(tenantLine = "true")
    List<Application> selectByIds(@Param("ids") Set<Long> ids);

    int insert(Application entity);

    int update(Application entity);

    int deleteById(Long id);

    /** 引用追踪：查找引用了指定流程的应用 */
    List<Application> selectByFlowId(@Param("flowId") Long flowId);
}
