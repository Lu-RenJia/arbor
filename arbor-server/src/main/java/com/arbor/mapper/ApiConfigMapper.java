package com.arbor.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.arbor.entity.ApiConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 接口配置数据访问层（MyBatis XML）
 *
 * @author gwl
 */
@Mapper
public interface ApiConfigMapper {

    /** 分页查询 */
    IPage<ApiConfig> selectPage(IPage<ApiConfig> page, @Param("keyword") String keyword,
                                @Param("sortField") String sortField, @Param("sortOrder") String sortOrder);

    /** 根据ID查询 */
    ApiConfig selectById(Long id);

    /** 根据apiId查询 */
    ApiConfig selectByApiId(String apiId);

    /** 插入（自动回填主键） */
    int insert(ApiConfig entity);

    /** 更新 */
    int update(ApiConfig entity);

    /** 删除 */
    int deleteById(Long id);

    /** 引用追踪：查询全部接口配置（用于扫描全局参数引用） */
    List<ApiConfig> selectAll();

    /** 内联资源：按流程ID查询 */
    List<ApiConfig> selectByFlowId(@Param("flowId") Long flowId);

    /** 内联资源：流程删除时级联清理 */
    int deleteByFlowId(@Param("flowId") Long flowId);
}
