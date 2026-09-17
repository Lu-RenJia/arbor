package com.arbor.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.arbor.entity.DbScript;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 数据库脚本数据访问层（MyBatis XML）
 *
 * @author gwl
 */
@Mapper
public interface DbScriptMapper {

    /** 分页查询 */
    IPage<DbScript> selectPage(IPage<DbScript> page, @Param("keyword") String keyword,
                               @Param("sortField") String sortField, @Param("sortOrder") String sortOrder);

    /** 根据ID查询 */
    DbScript selectById(Long id);

    /** 插入（自动回填主键） */
    int insert(DbScript entity);

    /** 更新 */
    int update(DbScript entity);

    /** 删除 */
    int deleteById(Long id);

    /** 引用追踪：查询全部数据库脚本（用于扫描全局参数引用） */
    List<DbScript> selectAll();

    /** 引用追踪：查找使用指定数据源的数据库脚本 */
    List<DbScript> selectByDatasourceId(@Param("datasourceId") Long datasourceId);

    /** 内联资源：按流程ID查询 */
    List<DbScript> selectByFlowId(@Param("flowId") Long flowId);

    /** 内联资源：流程删除时级联清理 */
    int deleteByFlowId(@Param("flowId") Long flowId);
}
