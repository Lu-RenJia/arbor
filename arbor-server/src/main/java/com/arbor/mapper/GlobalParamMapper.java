package com.arbor.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.arbor.entity.GlobalParam;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 全局参数数据访问层（MyBatis XML）
 *
 * @author gwl
 */
@Mapper
public interface GlobalParamMapper {

    /** 分页查询 */
    IPage<GlobalParam> selectPage(IPage<GlobalParam> page);

    /** 根据ID查询 */
    GlobalParam selectById(Long id);

    /** 根据参数名查询（返回任意一条匹配行） */
    GlobalParam selectByParamName(String paramName);

    /** 根据参数名查询所有行（跨环境） */
    List<GlobalParam> selectAllByParamName(String paramName);

    /** 插入（自动回填主键） */
    int insert(GlobalParam entity);

    /** 更新 */
    int update(GlobalParam entity);

    /** 删除 */
    int deleteById(Long id);

    /** 查询所有 */
    List<GlobalParam> selectAll();

    /** 批量根据ID查询 */
    List<GlobalParam> selectByIds(@Param("ids") java.util.Set<Long> ids);

    /** 批量根据参数名查询（每个参数名返回一条） */
    List<GlobalParam> selectByParamNames(@Param("names") java.util.Set<String> names);

    /** 查询去重的参数名列表（分页，支持搜索排序） */
    IPage<String> selectDistinctParamNames(IPage<String> page, @Param("keyword") String keyword,
                                           @Param("sortField") String sortField, @Param("sortOrder") String sortOrder);
}
