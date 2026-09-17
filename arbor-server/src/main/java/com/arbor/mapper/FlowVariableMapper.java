package com.arbor.mapper;

import com.arbor.entity.FlowVariable;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 流程变量池数据访问层
 *
 * @author gwl
 */
@Mapper
public interface FlowVariableMapper {

    /** 根据流程ID查询所有变量 */
    List<FlowVariable> selectByFlowId(Long flowId);

    /** 根据ID查询 */
    FlowVariable selectById(Long id);

    /** 新增 */
    int insert(FlowVariable entity);

    /** 修改 */
    int update(FlowVariable entity);

    /** 删除 */
    int deleteById(Long id);

    /** 批量删除 */
    int deleteByIds(@Param("ids") List<Long> ids);

    /** 引用追踪：查找引用了指定全局参数的流程变量 */
    List<FlowVariable> selectByParamId(@Param("paramId") Long paramId);

    /** 引用追踪：查找引用了指定数据源的流程变量 */
    List<FlowVariable> selectByDatasourceId(@Param("datasourceId") Long datasourceId);
}
