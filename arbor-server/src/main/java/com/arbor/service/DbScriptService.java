package com.arbor.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.arbor.common.AssertUtils;
import com.arbor.common.BusinessException;
import com.arbor.common.ErrorCode;
import com.arbor.common.PageResult;
import com.arbor.common.TenantContext;
import com.arbor.datasource.DynamicDataSourceManager;
import com.arbor.datasource.ParameterizedSql;
import com.arbor.datasource.SqlTypeDetector;
import com.arbor.datasource.pagination.PaginationStrategy;
import com.arbor.datasource.pagination.PaginationStrategyFactory;
import com.arbor.dto.DbScriptDTO;
import com.arbor.engine.ExecutionLogRecorder;
import com.arbor.engine.FlowEngine;
import com.arbor.engine.NodeResult;
import com.arbor.engine.ParamResolver;
import com.arbor.entity.Datasource;
import com.arbor.entity.DbScript;
import com.arbor.mapper.DbScriptMapper;
import com.arbor.util.RedisCacheUtil;
import com.arbor.vo.DbScriptVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 数据库脚本服务
 *
 * @author gwl
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DbScriptService {

    private final DbScriptMapper mapper;
    private final DatasourceService datasourceService;
    private final GlobalParamService globalParamService;
    private final DynamicDataSourceManager dynamicDataSourceManager;
    private final ParamResolver paramResolver;
    private final RedisCacheUtil redisCacheUtil;
    private final ExecutionLogRecorder executionLogRecorder;

    @Value("${arbor.sql.query-timeout-seconds:5}")
    private int defaultQueryTimeout;

    /** 数据库脚本缓存 key 前缀（按用户隔离，多租户插件按 user_id 过滤查询结果） */
    static final String DB_SCRIPT_CACHE_PREFIX = "db_script:user";

    private static final java.util.Set<String> ALLOWED_SORT_FIELDS = java.util.Set.of("create_time", "update_time", "id");
    private static final java.util.Set<String> ALLOWED_SORT_ORDERS = java.util.Set.of("ASC", "DESC");

    /**
     * 带缓存的按 ID 查询（key 按用户隔离，避免多租户过滤被缓存穿透）
     * 未命中缓存时回源数据库并写入缓存
     */
    private DbScript getByIdCached(Long id) {
        Long userId = TenantContext.getUserId();
        String key = RedisCacheUtil.buildKey(DB_SCRIPT_CACHE_PREFIX, (userId != null ? userId : 0), "id", id);
        return redisCacheUtil.get(key, DbScript.class)
                .orElseGet(() -> {
                    DbScript entity = mapper.selectById(id);
                    if (entity != null) {
                        redisCacheUtil.set(key, entity, RedisCacheUtil.randomizedTtl());
                    }
                    return entity;
                });
    }

    /** 清除某脚本在所有用户下的缓存副本（数据改动后调用） */
    private void evictCache(Long id) {
        redisCacheUtil.evictByPattern(RedisCacheUtil.buildKey(DB_SCRIPT_CACHE_PREFIX, "*", "id", id));
    }

    /**
     * 分页查询
     */
    public PageResult<DbScriptVO> page(int pageNum, int pageSize, String keyword, String sortField, String sortOrder) {
        if (sortField == null || !ALLOWED_SORT_FIELDS.contains(sortField)) sortField = "update_time";
        if (sortOrder == null || !ALLOWED_SORT_ORDERS.contains(sortOrder.toUpperCase())) sortOrder = "DESC";
        else sortOrder = sortOrder.toUpperCase();
        IPage<DbScript> page = new Page<>(pageNum, pageSize);
        IPage<DbScript> result = mapper.selectPage(page, keyword, sortField, sortOrder);
        List<DbScript> records = result.getRecords();
        // 一次 IN 查询取回本页涉及的数据源名称，避免逐行查库（N+1）
        Map<Long, String> datasourceNames = datasourceService.mapDatasourceNames(
                records.stream().map(DbScript::getDatasourceId).collect(Collectors.toList()));
        List<DbScriptVO> voList = records.stream().map(e -> {
            DbScriptVO vo = DbScriptVO.fromEntity(e);
            vo.setDatasourceName(datasourceNames.get(e.getDatasourceId()));
            return vo;
        }).collect(Collectors.toList());
        return PageResult.of(pageNum, pageSize, result.getTotal(), voList);
    }

    /**
     * 新增
     */
    @Transactional
    public DbScriptVO create(DbScriptDTO dto) {
        DbScript entity = new DbScript();
        entity.setName(dto.getName());
        entity.setDatasourceId(dto.getDatasourceId());
        entity.setSqlContent(dto.getSqlContent());
        entity.setReturnLimit(dto.getReturnLimit() != null ? dto.getReturnLimit() : 10);
        entity.setQueryTimeout(dto.getQueryTimeout());
        entity.setRemark(dto.getRemark());
        entity.setFlowId(dto.getFlowId());
        entity.setUid(java.util.UUID.randomUUID().toString());
        mapper.insert(entity);
        log.info("新增数据库脚本: {}", dto.getName());
        return DbScriptVO.fromEntity(entity);
    }

    /**
     * 修改
     */
    @Transactional
    public DbScriptVO update(Long id, DbScriptDTO dto) {
        DbScript entity = mapper.selectById(id);
        AssertUtils.notNull(entity, ErrorCode.DB_SCRIPT_NOT_FOUND);
        entity.setName(dto.getName());
        entity.setDatasourceId(dto.getDatasourceId());
        entity.setSqlContent(dto.getSqlContent());
        entity.setReturnLimit(dto.getReturnLimit() != null ? dto.getReturnLimit() : 10);
        entity.setQueryTimeout(dto.getQueryTimeout());
        entity.setRemark(dto.getRemark());
        mapper.update(entity);
        log.info("修改数据库脚本: id={}, name={}", id, dto.getName());
        evictCache(id);
        return DbScriptVO.fromEntity(entity);
    }

    /**
     * 删除
     */
    @Transactional
    public void delete(Long id) {
        DbScript entity = mapper.selectById(id);
        AssertUtils.notNull(entity, ErrorCode.DB_SCRIPT_NOT_FOUND);
        mapper.deleteById(id);
        log.info("删除数据库脚本: id={}, name={}", id, entity.getName());
        evictCache(id);
    }

    /**
     * 执行预览：解析全局参数后，在指定数据源上执行SQL，返回物理分页查询结果
     */
    public PageResult<Map<String, Object>> executePreview(Long dbScriptId, Long datasourceId, int pageNum, int pageSize) {
        DbScript script = getByIdCached(dbScriptId);
        AssertUtils.notNull(script, ErrorCode.DB_SCRIPT_NOT_FOUND);
        // 校验分页参数
        validatePageParams(pageNum, pageSize);

        long start = System.currentTimeMillis();
        try {
            Map<String, String> globalParams = globalParamService.getAllParams();
            ParameterizedSql psql = DatasourceService.resolveParameterized(script.getSqlContent(), globalParams);
            Datasource ds = datasourceService.getResolvedDatasource(datasourceId);
            // INFO 只记不含参数值的 SQL 模板，参数值降为 DEBUG（全局参数可能包含密钥）
            log.info("执行SQL预览: scriptId={}, datasourceId={}, page={}/{}, paramCount={}, sql={}",
                    dbScriptId, datasourceId, pageNum, pageSize, psql.paramCount(), psql.getSql());
            log.debug("SQL预览参数值: {}", psql.getParams());
            PageResult<Map<String, Object>> result = executePagedQuery(ds, psql, pageNum, pageSize,
                    resolveTimeout(script.getQueryTimeout()));
            // 执行日志同样只存模板：不将参数值落盘，避免敏感全局参数进入可查询的审计记录
            executionLogRecorder.save(null, script.getName(), FlowEngine.SOURCE_SCRIPT_TEST,
                    psql.getSql(), "SUCCESS", System.currentTimeMillis() - start, null);
            return result;
        } catch (Exception e) {
            executionLogRecorder.save(null, script.getName(), FlowEngine.SOURCE_SCRIPT_TEST,
                    script.getSqlContent(), "FAIL", System.currentTimeMillis() - start, e.getMessage());
            throw e;
        }
    }

    /**
     * 测试SQL：直接传入SQL内容和数据源ID执行（无需保存脚本），返回物理分页结果
     */
    public PageResult<Map<String, Object>> testSql(String sqlContent, Long datasourceId, int pageNum, int pageSize) {
        validatePageParams(pageNum, pageSize);

        long start = System.currentTimeMillis();
        try {
            Map<String, String> globalParams = globalParamService.getAllParams();
            ParameterizedSql psql = DatasourceService.resolveParameterized(sqlContent, globalParams);
            Datasource ds = datasourceService.getResolvedDatasource(datasourceId);
            log.info("测试SQL执行: datasourceId={}, page={}/{}, paramCount={}, sql={}",
                    datasourceId, pageNum, pageSize, psql.paramCount(), psql.getSql());
            log.debug("测试SQL参数值: {}", psql.getParams());
            PageResult<Map<String, Object>> result = executePagedQuery(ds, psql, pageNum, pageSize,
                    defaultQueryTimeout);
            executionLogRecorder.save(null, null, FlowEngine.SOURCE_SCRIPT_TEST,
                    psql.getSql(), "SUCCESS", System.currentTimeMillis() - start, null);
            return result;
        } catch (Exception e) {
            executionLogRecorder.save(null, null, FlowEngine.SOURCE_SCRIPT_TEST,
                    sqlContent, "FAIL", System.currentTimeMillis() - start, e.getMessage());
            throw e;
        }
    }

    /**
     * 流程引擎内部调用：使用脚本内置的 datasource_id 执行，返回全部数据
     */
    public List<Map<String, Object>> executeWithScriptDatasource(Long dbScriptId,
                                                                  Map<String, NodeResult> previousResults) {
        DbScript script = getByIdCached(dbScriptId);
        AssertUtils.notNull(script, ErrorCode.DB_SCRIPT_NOT_FOUND);
        AssertUtils.notNull(script.getDatasourceId(), ErrorCode.DB_SCRIPT_NO_DATASOURCE, script.getName());
        Map<String, String> globalParams = globalParamService.getAllParams();
        // 参数化解析：节点引用优先 → 全局参数兜底
        ParameterizedSql psql = paramResolver.resolveParameterized(script.getSqlContent(), globalParams, previousResults);
        Datasource ds = datasourceService.getResolvedDatasource(script.getDatasourceId());
        log.info("流程引擎执行SQL: scriptId={}, datasourceId={}", dbScriptId, ds.getId());
        int limit = script.getReturnLimit() != null ? script.getReturnLimit() : 10;
        return executeQuery(ds, psql, limit, resolveTimeout(script.getQueryTimeout()));
    }

    /**
     * 流程引擎内部调用：返回全部数据（不分页），指定数据源
     */
    public List<Map<String, Object>> executePreviewAll(Long dbScriptId, Long datasourceId) {
        DbScript script = getByIdCached(dbScriptId);
        AssertUtils.notNull(script, ErrorCode.DB_SCRIPT_NOT_FOUND);
        Map<String, String> globalParams = globalParamService.getAllParams();
        ParameterizedSql psql = DatasourceService.resolveParameterized(script.getSqlContent(), globalParams);
        Datasource ds = datasourceService.getResolvedDatasource(datasourceId);
        log.info("流程引擎执行SQL: scriptId={}, datasourceId={}", dbScriptId, datasourceId);
        int limit = script.getReturnLimit() != null ? script.getReturnLimit() : 10;
        return executeQuery(ds, psql, limit, resolveTimeout(script.getQueryTimeout()));
    }

    /**
     * 校验分页参数：pageNum >= 1, pageSize ∈ [1, 200]
     */
    private void validatePageParams(int pageNum, int pageSize) {
        AssertUtils.isTrue(pageNum >= 1, ErrorCode.DB_SCRIPT_PAGE_INVALID);
        AssertUtils.isTrue(pageSize >= 1 && pageSize <= 200, ErrorCode.DB_SCRIPT_PAGE_SIZE_INVALID);
    }

    /**
     * 执行 SQL（自动检测查询/DML，查询走分页，DML 返回影响行数）
     */
    private PageResult<Map<String, Object>> executePagedQuery(Datasource ds, ParameterizedSql psql,
                                                              int pageNum, int pageSize, int timeoutSec) {
        PaginationStrategy strategy = PaginationStrategyFactory.getStrategy(ds.getDbType());
        String sql = psql.getSql();

        // DML 分支：执行更新，不分页
        if (SqlTypeDetector.isDml(sql)) {
            try (Connection conn = dynamicDataSourceManager.getConnection(ds);
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setQueryTimeout(timeoutSec);
                psql.bindTo(ps);
                int affected = ps.executeUpdate();
                log.info("DML执行完成，影响行数: {}", affected);
                Map<String, Object> row = Map.of("affectedRows", affected);
                return PageResult.of(1, 1, 1, List.of(row));
            } catch (SQLTimeoutException e) {
                log.warn("SQL执行超时({}s)", timeoutSec);
                throw new BusinessException(ErrorCode.DB_SCRIPT_EXEC_FAILED, "SQL执行超时（" + timeoutSec + "秒）");
            } catch (Exception e) {
                log.error("DML执行失败", e);
                throw new BusinessException(ErrorCode.DML_EXEC_FAILED, e.getMessage());
            }
        }

        // SELECT 分支：参数化分页查询
        try (Connection conn = dynamicDataSourceManager.getConnection(ds)) {
            // 1. COUNT 查询（? 占位符透传，参数列表不变）
            long total;
            try (PreparedStatement countPs = conn.prepareStatement(strategy.countSql(sql))) {
                countPs.setQueryTimeout(timeoutSec);
                psql.bindTo(countPs);
                try (ResultSet countRs = countPs.executeQuery()) {
                    total = countRs.next() ? countRs.getLong(1) : 0;
                }
            }
            if (total == 0) {
                return PageResult.of(pageNum, pageSize, 0, List.of());
            }

            // 2. 分页查询（LIMIT/OFFSET 由策略硬编码到 SQL，参数列表不变）
            String pagedSql = strategy.wrapSql(sql, pageNum, pageSize);
            log.debug("物理分页SQL: {}", pagedSql);
            List<Map<String, Object>> rows = executeParameterizedQuery(conn, psql.withSql(pagedSql), timeoutSec);
            return PageResult.of(pageNum, pageSize, total, rows);
        } catch (SQLTimeoutException e) {
            log.warn("SQL执行超时({}s)", timeoutSec);
            throw new BusinessException(ErrorCode.DB_SCRIPT_EXEC_FAILED, "SQL执行超时（" + timeoutSec + "秒）");
        } catch (Exception e) {
            log.error("SQL执行失败", e);
            throw new BusinessException(ErrorCode.DB_SCRIPT_EXEC_FAILED, e.getMessage());
        }
    }

    /**
     * 直接执行查询（不分页，流程引擎用）
     * 自动检测 SQL 类型：SELECT 走 executeQuery，DML 走 executeUpdate
     */
    private List<Map<String, Object>> executeQuery(Datasource ds, ParameterizedSql psql, int timeoutSec) {
        String sql = psql.getSql();
        // DML 分支
        if (SqlTypeDetector.isDml(sql)) {
            try (Connection conn = dynamicDataSourceManager.getConnection(ds);
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setQueryTimeout(timeoutSec);
                psql.bindTo(ps);
                int affected = ps.executeUpdate();
                log.info("DML执行完成(流程引擎)，影响行数: {}", affected);
                return List.of(Map.of("affectedRows", affected));
            } catch (SQLTimeoutException e) {
                log.warn("SQL执行超时({}s)", timeoutSec);
                throw new BusinessException(ErrorCode.DB_SCRIPT_EXEC_FAILED, "SQL执行超时（" + timeoutSec + "秒）");
            } catch (Exception e) {
                log.error("SQL执行失败", e);
                throw new BusinessException(ErrorCode.DB_SCRIPT_EXEC_FAILED, e.getMessage());
            }
        }
        // SELECT 分支
        try (Connection conn = dynamicDataSourceManager.getConnection(ds)) {
            return executeParameterizedQuery(conn, psql, timeoutSec);
        } catch (SQLTimeoutException e) {
            log.warn("SQL执行超时({}s)", timeoutSec);
            throw new BusinessException(ErrorCode.DB_SCRIPT_EXEC_FAILED, "SQL执行超时（" + timeoutSec + "秒）");
        } catch (Exception e) {
            log.error("SQL执行失败", e);
            throw new BusinessException(ErrorCode.DB_SCRIPT_EXEC_FAILED, e.getMessage());
        }
    }

    /** 带 limit 的执行（流程引擎用） */
    private List<Map<String, Object>> executeQuery(Datasource ds, ParameterizedSql psql, int limit, int timeoutSec) {
        if (SqlTypeDetector.isDml(psql.getSql())) {
            return executeQuery(ds, psql, timeoutSec);
        }
        PaginationStrategy strategy = PaginationStrategyFactory.getStrategy(ds.getDbType());
        // 分页策略只在外层包裹/追加硬编码整数，不引入新的 ?，故参数列表可原样沿用
        return executeQuery(ds, psql.withSql(strategy.wrapSql(psql.getSql(), 1, limit)), timeoutSec);
    }

    /** 执行参数化查询并返回结果列表 */
    private List<Map<String, Object>> executeParameterizedQuery(Connection conn, ParameterizedSql psql,
                                                                int timeoutSec) throws SQLException {
        List<Map<String, Object>> rows = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(psql.getSql())) {
            ps.setQueryTimeout(timeoutSec);
            psql.bindTo(ps);
            try (ResultSet rs = ps.executeQuery()) {
                ResultSetMetaData meta = rs.getMetaData();
                int columnCount = meta.getColumnCount();
                while (rs.next()) {
                    Map<String, Object> row = new HashMap<>();
                    for (int i = 1; i <= columnCount; i++) {
                        row.put(meta.getColumnName(i), rs.getObject(i));
                    }
                    rows.add(row);
                }
            }
        }
        return rows;
    }

    /** 计算有效超时秒数：优先用脚本配置，否则用系统默认 */
    private int resolveTimeout(Integer scriptTimeout) {
        return scriptTimeout != null && scriptTimeout > 0 ? scriptTimeout : defaultQueryTimeout;
    }
}
