package com.arbor.mapper;

import com.arbor.entity.RedisKeyPrefix;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * Redis 业务前缀 Mapper
 * <p>
 * {@code redis_key_prefix} 未加入 {@code MybatisPlusConfig.SHARED_TABLES}，
 * 故所有查询自动受租户过滤（与 redis_datasource / redis_script 一致）。
 * </p>
 */
@Mapper
public interface RedisKeyPrefixMapper {

    /** 某数据源下登记的全部前缀（配置页下拉、数据源页前缀管理用） */
    List<RedisKeyPrefix> selectByDatasourceId(@Param("datasourceId") Long datasourceId);

    RedisKeyPrefix selectById(Long id);

    /** 批量按 id 查 —— 供 RedisScriptVO 回显前缀值，一次 IN 查询避免 N+1 */
    List<RedisKeyPrefix> selectByIds(@Param("ids") List<Long> ids);

    /** 同数据源下前缀值查重（唯一索引 uk_redis_prefix_ds 的应用层前置校验，给出友好报错） */
    RedisKeyPrefix selectByDatasourceIdAndPrefix(@Param("datasourceId") Long datasourceId,
                                                 @Param("prefix") String prefix);

    int insert(RedisKeyPrefix entity);

    int update(RedisKeyPrefix entity);

    int deleteById(Long id);

    /** 删除某数据源下登记的全部前缀（数据源删除时级联，避免留下指向已删数据源的孤儿前缀） */
    int deleteByDatasourceId(@Param("datasourceId") Long datasourceId);
}
