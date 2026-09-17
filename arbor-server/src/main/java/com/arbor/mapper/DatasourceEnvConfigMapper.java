package com.arbor.mapper;

import com.arbor.entity.DatasourceEnvConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 数据源-环境配置数据访问层（MyBatis XML）
 *
 * @author gwl
 */
@Mapper
public interface DatasourceEnvConfigMapper {

    /** 按数据源ID+环境ID查询 */
    DatasourceEnvConfig selectByDsAndEnv(@Param("datasourceId") Long datasourceId, @Param("envId") Long envId);

    /** 按数据源ID查询所有环境配置 */
    List<DatasourceEnvConfig> selectByDatasourceId(Long datasourceId);

    /** 插入 */
    int insert(DatasourceEnvConfig entity);

    /** 更新 */
    int update(DatasourceEnvConfig entity);

    /** 按数据源ID+环境ID删除 */
    int deleteByDsAndEnv(@Param("datasourceId") Long datasourceId, @Param("envId") Long envId);
}
