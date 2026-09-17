package com.arbor.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.arbor.common.AssertUtils;
import com.arbor.common.EnvironmentContext;
import com.arbor.common.ErrorCode;
import com.arbor.common.PageResult;
import com.arbor.common.TenantContext;
import com.arbor.datasource.DynamicDataSourceManager;
import com.arbor.datasource.ParameterizedSql;
import com.arbor.datasource.SqlParameterizer;
import com.arbor.dto.DatasourceDTO;
import com.arbor.entity.Datasource;
import com.arbor.entity.DatasourceEnvConfig;
import com.arbor.entity.Environment;
import com.arbor.mapper.DatasourceEnvConfigMapper;
import com.arbor.mapper.DatasourceMapper;
import com.arbor.util.AesUtil;
import com.arbor.util.RedisCacheUtil;
import com.arbor.vo.DatasourceVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 数据源管理服务
 *
 * @author gwl
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DatasourceService {

    private final DatasourceMapper mapper;
    private final DatasourceEnvConfigMapper envConfigMapper;
    private final GlobalParamService globalParamService;
    private final DynamicDataSourceManager dynamicDataSourceManager;
    private final EnvironmentService environmentService;
    private final AesUtil aesUtil;
    private final RedisCacheUtil redisCacheUtil;

    /** 密码脱敏占位符 */
    public static final String PASSWORD_MASK = "******";

    private static final java.util.Set<String> ALLOWED_SORT_FIELDS = java.util.Set.of("create_time", "update_time", "id");
    private static final java.util.Set<String> ALLOWED_SORT_ORDERS = java.util.Set.of("ASC", "DESC");
    /** 数据源缓存 key 前缀（按用户隔离） */
    static final String DS_CACHE_PREFIX = "datasource:user";
    static final String DS_ENV_CACHE_PREFIX = "datasource_env:user";
    /** ${paramName} 占位符匹配 */
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\$\\{([^}]+)}");

    /** 分页查询 */
    public PageResult<DatasourceVO> page(int pageNum, int pageSize, String keyword, String sortField, String sortOrder) {
        if (sortField == null || !ALLOWED_SORT_FIELDS.contains(sortField)) sortField = "update_time";
        if (sortOrder == null || !ALLOWED_SORT_ORDERS.contains(sortOrder.toUpperCase())) sortOrder = "DESC";
        else sortOrder = sortOrder.toUpperCase();
        IPage<Datasource> page = new Page<>(pageNum, pageSize);
        IPage<Datasource> result = mapper.selectPage(page, keyword, sortField, sortOrder);
        List<DatasourceVO> voList = result.getRecords().stream().map(DatasourceVO::fromEntity).collect(Collectors.toList());
        return PageResult.of(pageNum, pageSize, result.getTotal(), voList);
    }

    /** 新增 */
    @Transactional
    public DatasourceVO create(DatasourceDTO dto) {
        AssertUtils.hasText(dto.getPassword(), ErrorCode.DATASOURCE_PASSWORD_REQUIRED);
        Datasource entity = new Datasource();
        entity.setName(dto.getName());
        entity.setDbType(dto.getDbType().toUpperCase());
        entity.setHost(dto.getHost());
        entity.setPort(dto.getPort());
        entity.setDatabaseName(dto.getDatabaseName());
        entity.setUsername(dto.getUsername());
        entity.setPassword(aesUtil.encrypt(dto.getPassword()));
        entity.setRemark(dto.getRemark());
        if (isAdmin()) {
            entity.setPoolMinIdle(dto.getPoolMinIdle());
            entity.setPoolMaxSize(dto.getPoolMaxSize());
            entity.setPoolIdleTimeoutSec(dto.getPoolIdleTimeoutSec());
            entity.setPoolMaxLifetimeSec(dto.getPoolMaxLifetimeSec());
            entity.setPoolConnTimeoutSec(dto.getPoolConnTimeoutSec());
        }
        entity.setUid(java.util.UUID.randomUUID().toString());
        mapper.insert(entity);
        log.info("新增数据源: {}", dto.getName());
        return DatasourceVO.fromEntity(entity);
    }

    /** 修改 */
    @Transactional
    public DatasourceVO update(Long id, DatasourceDTO dto) {
        Datasource entity = mapper.selectById(id);
        AssertUtils.notNull(entity, ErrorCode.DATASOURCE_NOT_FOUND);
        entity.setName(dto.getName());
        entity.setDbType(dto.getDbType().toUpperCase());
        entity.setHost(dto.getHost());
        entity.setPort(dto.getPort());
        entity.setDatabaseName(dto.getDatabaseName());
        entity.setUsername(dto.getUsername());
        if (Boolean.TRUE.equals(dto.getModifyPassword())
                && dto.getPassword() != null && !dto.getPassword().isBlank()
                && !PASSWORD_MASK.equals(dto.getPassword())) {
            entity.setPassword(aesUtil.encrypt(dto.getPassword()));
        }
        entity.setRemark(dto.getRemark());
        if (isAdmin()) {
            entity.setPoolMinIdle(dto.getPoolMinIdle());
            entity.setPoolMaxSize(dto.getPoolMaxSize());
            entity.setPoolIdleTimeoutSec(dto.getPoolIdleTimeoutSec());
            entity.setPoolMaxLifetimeSec(dto.getPoolMaxLifetimeSec());
            entity.setPoolConnTimeoutSec(dto.getPoolConnTimeoutSec());
        }
        mapper.update(entity);
        log.info("修改数据源: id={}, name={}", id, dto.getName());
        dynamicDataSourceManager.evictPool(id);
        evictCache(id);
        return DatasourceVO.fromEntity(entity);
    }

    private boolean isAdmin() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;
        return auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    }

    /** 删除 */
    @Transactional
    public void delete(Long id) {
        Datasource entity = mapper.selectById(id);
        AssertUtils.notNull(entity, ErrorCode.DATASOURCE_NOT_FOUND);
        mapper.deleteById(id);
        log.info("删除数据源: id={}, name={}", id, entity.getName());
        dynamicDataSourceManager.evictPool(id);
        evictCache(id);
    }

    /** 测试连接（自动感知环境） */
    public boolean testConnection(Long id) {
        Long envId = resolveEnvId();
        Datasource ds = getResolvedDatasource(id, envId);
        log.info("测试数据源连接: {} (env={}) -> {}", ds.getName(), envId, DynamicDataSourceManager.buildJdbcUrl(ds));
        return dynamicDataSourceManager.testConnection(ds);
    }

    /** 测试连接（指定环境） */
    public boolean testConnection(Long id, Long envId) {
        Datasource ds = getResolvedDatasource(id, envId);
        log.info("测试数据源连接: {} (env={}) -> {}", ds.getName(), envId, DynamicDataSourceManager.buildJdbcUrl(ds));
        return dynamicDataSourceManager.testConnection(ds);
    }

    /** 测试连接（未保存的 DTO） */
    public boolean testConnectionRaw(DatasourceDTO dto) {
        Datasource ds = new Datasource();
        ds.setName(dto.getName());
        ds.setDbType(dto.getDbType().toUpperCase());
        ds.setHost(dto.getHost());
        ds.setPort(dto.getPort());
        ds.setDatabaseName(dto.getDatabaseName());
        ds.setUsername(dto.getUsername());
        ds.setPassword(aesUtil.encrypt(dto.getPassword()));
        Map<String, String> globalParams = globalParamService.getAllParams();
        ds = resolvePlaceholders(ds, globalParams);
        log.info("测试数据源连接(未保存): {} -> {}", ds.getName(), DynamicDataSourceManager.buildJdbcUrl(ds));
        return dynamicDataSourceManager.testConnection(ds);
    }

    private Datasource getByIdCached(Long id) {
        Long userId = TenantContext.getUserId();
        String key = RedisCacheUtil.buildKey(DS_CACHE_PREFIX, (userId != null ? userId : 0), "id", id);
        return redisCacheUtil.get(key, Datasource.class)
                .orElseGet(() -> {
                    Datasource entity = mapper.selectById(id);
                    if (entity != null) {
                        redisCacheUtil.set(key, entity, RedisCacheUtil.randomizedTtl());
                    }
                    return entity;
                });
    }

    private void evictCache(Long id) {
        redisCacheUtil.evictByPattern(RedisCacheUtil.buildKey(DS_CACHE_PREFIX, "*", "id", id));
    }

    /**
     * 批量查数据源名称（id → name），供列表页回显
     * <p>一次 IN 查询，避免逐行查库（N+1）。调用方传 null 元素或空集合都安全 ——
     * 空集合会拼出 {@code IN ()} 语法错误，故此处必须先判空。</p>
     */
    public Map<Long, String> mapDatasourceNames(Collection<Long> ids) {
        if (ids == null) return Map.of();
        Set<Long> valid = ids.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (valid.isEmpty()) return Map.of();
        return mapper.selectByIds(valid).stream()
                .collect(Collectors.toMap(Datasource::getId, Datasource::getName, (a, b) -> a));
    }

    /**
     * 获取解析全局参数后的数据源配置（自动感知当前环境）
     */
    public Datasource getResolvedDatasource(Long id) {
        return getResolvedDatasource(id, resolveEnvId());
    }

    /** 解析当前环境ID：优先 EnvironmentContext，否则查用户默认环境 */
    private Long resolveEnvId() {
        Long envId = EnvironmentContext.getEnvIdOrDefault();
        return envId != null ? envId : environmentService.getDefaultEnvId();
    }

    /**
     * 获取解析全局参数后的数据源配置（指定环境）
     * 环境配置覆写基表：host/port/databaseName/username/password 及连接池参数
     */
    public Datasource getResolvedDatasource(Long id, Long envId) {
        Datasource ds = getByIdCached(id);
        AssertUtils.notNull(ds, ErrorCode.DATASOURCE_NOT_FOUND);
        // 应用环境覆写配置
        DatasourceEnvConfig envConfig = getEnvConfigCached(id, envId);
        Datasource merged = new Datasource();
        merged.setId(ds.getId());
        merged.setName(ds.getName());
        merged.setDbType(ds.getDbType());
        merged.setHost(envConfig != null && envConfig.getHost() != null ? envConfig.getHost() : ds.getHost());
        merged.setPort(envConfig != null && envConfig.getPort() != null ? envConfig.getPort() : ds.getPort());
        merged.setDatabaseName(envConfig != null && envConfig.getDatabaseName() != null ? envConfig.getDatabaseName() : ds.getDatabaseName());
        merged.setUsername(envConfig != null && envConfig.getUsername() != null ? envConfig.getUsername() : ds.getUsername());
        merged.setPassword(envConfig != null && envConfig.getPassword() != null ? envConfig.getPassword() : ds.getPassword());
        merged.setRemark(ds.getRemark());
        merged.setCreateTime(ds.getCreateTime());
        merged.setUpdateTime(ds.getUpdateTime());
        // 连接池参数：环境配置优先，否则用基表
        merged.setPoolMinIdle(envConfig != null && envConfig.getPoolMinIdle() != null ? envConfig.getPoolMinIdle() : ds.getPoolMinIdle());
        merged.setPoolMaxSize(envConfig != null && envConfig.getPoolMaxSize() != null ? envConfig.getPoolMaxSize() : ds.getPoolMaxSize());
        merged.setPoolIdleTimeoutSec(envConfig != null && envConfig.getPoolIdleTimeoutSec() != null ? envConfig.getPoolIdleTimeoutSec() : ds.getPoolIdleTimeoutSec());
        merged.setPoolMaxLifetimeSec(envConfig != null && envConfig.getPoolMaxLifetimeSec() != null ? envConfig.getPoolMaxLifetimeSec() : ds.getPoolMaxLifetimeSec());
        merged.setPoolConnTimeoutSec(envConfig != null && envConfig.getPoolConnTimeoutSec() != null ? envConfig.getPoolConnTimeoutSec() : ds.getPoolConnTimeoutSec());

        Map<String, String> globalParams = globalParamService.getAllParams(envId);
        return resolvePlaceholders(merged, globalParams);
    }

    /** 查询数据源在各环境下的配置（供前端"环境配置"子列表） */
    public List<DatasourceVO.DsEnvConfig> listEnvConfigs(Long datasourceId) {
        List<DatasourceEnvConfig> configs = envConfigMapper.selectByDatasourceId(datasourceId);
        Map<Long, Environment> envMap = environmentService.getEnvMap();
        return configs.stream().map(c -> {
            Environment env = envMap.get(c.getEnvId());
            return DatasourceVO.DsEnvConfig.builder()
                    .envId(c.getEnvId())
                    .envName(env != null ? env.getName() : "未知环境")
                    .envCode(env != null ? env.getCode() : "")
                    .status(env == null || env.getIsDeleted() == 1 ? "已停用" : "正常")
                    .host(c.getHost())
                    .port(c.getPort())
                    .databaseName(c.getDatabaseName())
                    .username(c.getUsername())
                    .poolMinIdle(c.getPoolMinIdle())
                    .poolMaxSize(c.getPoolMaxSize())
                    .build();
        }).collect(Collectors.toList());
    }

    /** 保存/更新某数据源在某环境下的配置 */
    @Transactional
    public void saveEnvConfig(Long datasourceId, Long envId, DatasourceEnvConfig config) {
        Datasource ds = mapper.selectById(datasourceId);
        AssertUtils.notNull(ds, ErrorCode.DATASOURCE_NOT_FOUND);
        DatasourceEnvConfig exist = envConfigMapper.selectByDsAndEnv(datasourceId, envId);
        config.setDatasourceId(datasourceId);
        config.setEnvId(envId);
        // 归属者始终为所属数据源的拥有者：普通用户=自己；admin 代编辑=数据源真实归属者
        config.setUserId(ds.getUserId());
        if (config.getPassword() != null && !config.getPassword().isBlank()
                && !PASSWORD_MASK.equals(config.getPassword())) {
            config.setPassword(aesUtil.encrypt(config.getPassword()));
        }
        if (exist != null) {
            config.setId(exist.getId());
            envConfigMapper.update(config);
        } else {
            envConfigMapper.insert(config);
        }
        evictEnvCache(datasourceId, envId);
        dynamicDataSourceManager.evictPool(datasourceId);
    }

    /** 删除某数据源在某环境下的配置 */
    @Transactional
    public void deleteEnvConfig(Long datasourceId, Long envId) {
        envConfigMapper.deleteByDsAndEnv(datasourceId, envId);
        evictEnvCache(datasourceId, envId);
        dynamicDataSourceManager.evictPool(datasourceId);
    }

    /** 环境配置缓存（空结果也缓存，避免重复穿透 DB） */
    private DatasourceEnvConfig getEnvConfigCached(Long datasourceId, Long envId) {
        Long userId = TenantContext.getUserId();
        String key = RedisCacheUtil.buildKey(DS_ENV_CACHE_PREFIX, (userId != null ? userId : 0), "ds", datasourceId, "env", envId);
        return redisCacheUtil.get(key, DatasourceEnvConfig.class)
                .orElseGet(() -> {
                    DatasourceEnvConfig config = envConfigMapper.selectByDsAndEnv(datasourceId, envId);
                    if (config != null) {
                        redisCacheUtil.set(key, config, RedisCacheUtil.randomizedTtl());
                    } else {
                        // 空结果也缓存，下次命中 Optional 有值，不再查 DB
                        redisCacheUtil.set(key, new DatasourceEnvConfig(), RedisCacheUtil.randomizedTtl());
                    }
                    return config;
                });
    }

    private void evictEnvCache(Long datasourceId, Long envId) {
        redisCacheUtil.evictByPattern(RedisCacheUtil.buildKey(DS_ENV_CACHE_PREFIX, "*", "ds", datasourceId, "env", envId));
    }

    Datasource resolvePlaceholders(Datasource ds, Map<String, String> globalParams) {
        Datasource resolved = new Datasource();
        resolved.setId(ds.getId());
        resolved.setName(ds.getName());
        resolved.setDbType(ds.getDbType());
        resolved.setHost(resolve(ds.getHost(), globalParams));
        resolved.setPort(ds.getPort());
        resolved.setDatabaseName(resolve(ds.getDatabaseName(), globalParams));
        resolved.setUsername(resolve(ds.getUsername(), globalParams));
        resolved.setPassword(ds.getPassword());
        resolved.setRemark(ds.getRemark());
        resolved.setPoolMinIdle(ds.getPoolMinIdle());
        resolved.setPoolMaxSize(ds.getPoolMaxSize());
        resolved.setPoolIdleTimeoutSec(ds.getPoolIdleTimeoutSec());
        resolved.setPoolMaxLifetimeSec(ds.getPoolMaxLifetimeSec());
        resolved.setPoolConnTimeoutSec(ds.getPoolConnTimeoutSec());
        resolved.setCreateTime(ds.getCreateTime());
        resolved.setUpdateTime(ds.getUpdateTime());
        return resolved;
    }

    public static String resolve(String template, Map<String, String> params) {
        if (template == null || !template.contains("${")) {
            return template;
        }
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(template);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String key = matcher.group(1);
            if (isInComment(template, matcher.start())) {
                matcher.appendReplacement(sb, "\\${" + key + "}");
                continue;
            }
            String value = params.get(key);
            AssertUtils.notNull(value, ErrorCode.GLOBAL_PARAM_MISSING, key);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /**
     * 参数化解析模板中的 ${xxx} 占位符，返回 ParameterizedSql（? 占位 + 参数值列表）
     * <p>用于全局参数场景，配合 PreparedStatement 防止 SQL 注入。
     * 具体的词法上下文处理（字面量/标识符/注释）统一由 {@link SqlParameterizer} 完成，
     * 与 {@code ParamResolver.resolveParameterized} 走同一套逻辑。</p>
     */
    public static ParameterizedSql resolveParameterized(String template, Map<String, String> params) {
        return SqlParameterizer.parameterize(template, PLACEHOLDER_PATTERN, key -> {
            String value = params == null ? null : params.get(key);
            AssertUtils.notNull(value, ErrorCode.GLOBAL_PARAM_MISSING, key);
            return value;
        });
    }

    /**
     * 判断 pos 位置是否处于注释中
     * <p>仅供 {@link #resolve} 使用 —— 它处理的是 URL、请求头、请求体等非 SQL 模板，
     * 故识别 {@code //}（并排除 {@code ://} 协议分隔符）与 {@code /* *}{@code /}。
     * SQL 模板请用 {@link SqlParameterizer}：SQL 不认 {@code //}，却认 {@code --} 与 {@code #}。</p>
     */
    private static boolean isInComment(String template, int pos) {
        // 行注释（//）：需排除 URL 协议中的 "://"（如 http://、https://），避免误判 URL 里的占位符为注释
        int lastNewline = template.lastIndexOf("\n", pos);
        int searchFrom = pos;
        while (true) {
            int lineComment = template.lastIndexOf("//", searchFrom);
            if (lineComment < 0 || lineComment <= lastNewline) break;
            // "://" 是协议分隔符而非注释，继续向前查找真正的行注释
            if (lineComment > 0 && template.charAt(lineComment - 1) == ':') {
                searchFrom = lineComment - 1;
                continue;
            }
            return true;
        }
        int lastBlockOpen = template.lastIndexOf("/*", pos);
        if (lastBlockOpen >= 0) {
            int lastBlockClose = template.lastIndexOf("*/", pos);
            if (lastBlockOpen > lastBlockClose) return true;
        }
        return false;
    }
}
