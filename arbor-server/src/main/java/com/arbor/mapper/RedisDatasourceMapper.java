package com.arbor.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.arbor.entity.RedisDatasource;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;
import java.util.Set;

@Mapper
public interface RedisDatasourceMapper {
    IPage<RedisDatasource> selectPage(IPage<RedisDatasource> page, @Param("keyword") String keyword,
                                      @Param("sortField") String sortField, @Param("sortOrder") String sortOrder);
    RedisDatasource selectById(Long id);
    /**
     * 批量按ID查询（只返回 id/name，不含密码密文）
     * <p>调用方须保证 ids 非空 —— 空集合会拼出 {@code IN ()} 语法错误。</p>
     */
    List<RedisDatasource> selectByIds(@Param("ids") Set<Long> ids);
    int insert(RedisDatasource entity);
    int update(RedisDatasource entity);
    int deleteById(Long id);
}
