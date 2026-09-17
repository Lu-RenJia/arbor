package com.arbor.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.arbor.entity.Datasource;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Set;

/**
 * 数据源数据访问层（MyBatis XML）
 *
 * @author gwl
 */
@Mapper
public interface DatasourceMapper {

    /** 分页查询 */
    IPage<Datasource> selectPage(IPage<Datasource> page, @Param("keyword") String keyword,
                                  @Param("sortField") String sortField, @Param("sortOrder") String sortOrder);

    /** 根据ID查询 */
    Datasource selectById(Long id);

    /**
     * 批量按ID查询（只返回 id/name，不含密码密文）
     * <p>调用方须保证 ids 非空 —— 空集合会拼出 {@code IN ()} 语法错误。</p>
     */
    List<Datasource> selectByIds(@Param("ids") Set<Long> ids);

    /** 插入（自动回填主键） */
    int insert(Datasource entity);

    /** 更新 */
    int update(Datasource entity);

    /** 删除 */
    int deleteById(Long id);

    /** 引用追踪：查询全部数据源（用于扫描全局参数引用） */
    List<Datasource> selectAll();
}
