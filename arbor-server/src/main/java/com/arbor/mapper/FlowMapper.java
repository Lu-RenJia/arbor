package com.arbor.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.arbor.entity.Flow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 流程定义数据访问层（MyBatis XML）
 *
 * @author gwl
 */
@Mapper
public interface FlowMapper {

    /** 分页查询（支持名称模糊、节点数过滤、排序） */
    IPage<Flow> selectPage(IPage<Flow> page,
                             @Param("name") String name, @Param("nodeCount") Integer nodeCount,
                             @Param("sortField") String sortField, @Param("sortOrder") String sortOrder);

    /** 根据ID查询 */
    Flow selectById(Long id);

    /** 插入 */
    int insert(Flow entity);

    /** 更新 */
    int update(Flow entity);

    /** 更新状态 */
    int updateStatus(@Param("id") Long id, @Param("status") Integer status);

    /** 更新节点数量 */
    int updateNodeCount(@Param("id") Long id, @Param("nodeCount") Integer nodeCount);

    /** 删除 */
    int deleteById(Long id);

    /** 同用户下指定名称的流程数量（用于名称唯一性校验） */
    long countByNameAndUser(@Param("name") String name, @Param("userId") Long userId);

    /** 批量根据ID查询 */
    List<Flow> selectByIds(@Param("ids") java.util.Set<Long> ids);

    /** 批量根据ID查询（绕过租户过滤，用于广场应用列表） */
    @InterceptorIgnore(tenantLine = "true")
    List<Flow> selectByIdsIgnoreTenant(@Param("ids") java.util.Set<Long> ids);
}
