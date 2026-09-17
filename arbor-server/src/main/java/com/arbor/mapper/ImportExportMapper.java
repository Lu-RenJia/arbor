package com.arbor.mapper;

import com.arbor.entity.*;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 导入导出专用数据访问层 — uid 匹配查询与覆盖写入
 * （租户过滤由 MyBatis-Plus 租户插件自动注入 user_id 条件）
 *
 * @author gwl
 */
@Mapper
public interface ImportExportMapper {

    // ==================== flow ====================
    Flow selectFlowById(Long id);
    Flow selectFlowByUid(@Param("uid") String uid);
    int updateFlowUid(@Param("id") Long id, @Param("uid") String uid);

    // ==================== application ====================
    Application selectAppById(Long id);
    Application selectAppByUid(@Param("uid") String uid);
    int updateAppUid(@Param("id") Long id, @Param("uid") String uid);

    // ==================== db_script ====================
    DbScript selectDbScriptById(Long id);
    DbScript selectDbScriptByUid(@Param("uid") String uid);
    List<DbScript> selectDbScriptByIds(@Param("ids") java.util.Set<Long> ids);
    List<DbScript> selectDbScriptsByFlowId(@Param("flowId") Long flowId);
    int updateDbScriptUid(@Param("id") Long id, @Param("uid") String uid);

    // ==================== program_script ====================
    ProgramScript selectProgramScriptById(Long id);
    ProgramScript selectProgramScriptByUid(@Param("uid") String uid);
    ProgramScript selectProgramScriptByScriptId(@Param("scriptId") String scriptId);
    List<ProgramScript> selectProgramScriptByIds(@Param("ids") java.util.Set<Long> ids);
    List<ProgramScript> selectProgramScriptsByFlowId(@Param("flowId") Long flowId);
    int updateProgramScriptUid(@Param("id") Long id, @Param("uid") String uid);

    // ==================== api_config ====================
    ApiConfig selectApiConfigById(Long id);
    ApiConfig selectApiConfigByUid(@Param("uid") String uid);
    List<ApiConfig> selectApiConfigByIds(@Param("ids") java.util.Set<Long> ids);
    List<ApiConfig> selectApiConfigsByFlowId(@Param("flowId") Long flowId);
    int updateApiConfigUid(@Param("id") Long id, @Param("uid") String uid);

    // ==================== redis_script ====================
    RedisScript selectRedisScriptById(Long id);
    RedisScript selectRedisScriptByUid(@Param("uid") String uid);
    List<RedisScript> selectRedisScriptByIds(@Param("ids") java.util.Set<Long> ids);
    List<RedisScript> selectRedisScriptsByFlowId(@Param("flowId") Long flowId);
    int updateRedisScriptUid(@Param("id") Long id, @Param("uid") String uid);

    // ==================== datasource ====================
    Datasource selectDatasourceById(Long id);
    Datasource selectDatasourceIdByName(@Param("name") String name);
    int updateDatasourceUid(@Param("id") Long id, @Param("uid") String uid);

    // ==================== redis_datasource ====================
    RedisDatasource selectRedisDatasourceById(Long id);
    RedisDatasource selectRedisDatasourceByName(@Param("name") String name);
    int updateRedisDatasourceUid(@Param("id") Long id, @Param("uid") String uid);

    // ==================== 导入写入（insert/overwrite 均显式携带 uid） ====================
    int insertFlow(Flow entity);
    int overwriteFlow(Flow entity);

    int insertApp(Application entity);
    int overwriteApp(Application entity);

    int insertDbScript(DbScript entity);
    int overwriteDbScript(DbScript entity);

    int insertProgramScript(ProgramScript entity);
    int overwriteProgramScript(ProgramScript entity);

    int insertApiConfig(ApiConfig entity);
    int overwriteApiConfig(ApiConfig entity);

    int insertRedisScript(RedisScript entity);
    int overwriteRedisScript(RedisScript entity);
}
