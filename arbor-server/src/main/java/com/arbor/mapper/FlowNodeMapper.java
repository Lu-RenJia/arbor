package com.arbor.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.arbor.entity.FlowNode;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 流程节点数据访问层（MyBatis XML）
 *
 * @author gwl
 */
@Mapper
public interface FlowNodeMapper {

    /** 根据流程ID查询所有节点（按排序） */
    List<FlowNode> selectByFlowId(Long flowId);

    /** 根据流程ID查询所有节点（绕过租户过滤，用于执行广场共享应用） */
    @InterceptorIgnore(tenantLine = "true")
    List<FlowNode> selectByFlowIdIgnoreTenant(Long flowId);

    /** 根据ID查询 */
    FlowNode selectById(Long id);

    /** 插入 */
    int insert(FlowNode entity);

    /** 批量插入 */
    int insertBatch(@Param("list") List<FlowNode> list);

    /** 更新 */
    int update(FlowNode entity);

    /** 根据流程ID删除所有节点 */
    int deleteByFlowId(Long flowId);

    /** 批量根据ID查询 */
    List<FlowNode> selectByIds(@Param("ids") java.util.Set<Long> ids);

    /** 引用追踪：按节点类型 + config JSON 字段精准查找引用了指定资源的节点 */
    List<FlowNode> selectByConfigRef(@Param("nodeType") String nodeType,
                                     @Param("jsonKey") String jsonKey,
                                     @Param("refId") String refId);
}
