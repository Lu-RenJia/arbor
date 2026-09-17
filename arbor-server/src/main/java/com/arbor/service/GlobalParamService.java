package com.arbor.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.arbor.common.AssertUtils;
import com.arbor.common.EnvironmentContext;
import com.arbor.common.ErrorCode;
import com.arbor.common.PageResult;
import com.arbor.common.TenantContext;
import com.arbor.dto.GlobalParamDTO;
import com.arbor.entity.Environment;
import com.arbor.entity.GlobalParam;
import com.arbor.entity.GlobalParamValue;
import com.arbor.mapper.GlobalParamMapper;
import com.arbor.mapper.GlobalParamValueMapper;
import com.arbor.util.RedisCacheUtil;
import com.arbor.vo.GlobalParamVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 全局参数服务
 *
 * @author gwl
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GlobalParamService {

    private final GlobalParamMapper mapper;
    private final GlobalParamValueMapper valueMapper;
    private final RedisCacheUtil redisCacheUtil;
    private final EnvironmentService environmentService;

    /** Redis 缓存 key 前缀 */
    static final String GP_CACHE_PREFIX = "global_params:user";
    static final String GP_ENV_VALUE_PREFIX = "global_params:env";

    private static final java.util.Set<String> ALLOWED_SORT_FIELDS = java.util.Set.of("param_name", "create_time", "update_time");
    private static final java.util.Set<String> ALLOWED_SORT_ORDERS = java.util.Set.of("ASC", "DESC");

    /** 内存缓存有效期（毫秒），默认60秒 */
    private static final long CACHE_TTL_MS = 60_000;

    /** Per-(user,env) JVM 缓存：key=userId:envId:skipFlag → CacheEntry（隔离后不同用户/上下文的参数集不同，必须区分） */
    private final ConcurrentHashMap<String, CacheEntry> envCache = new ConcurrentHashMap<>();

    private static class CacheEntry {
        final Map<String, String> params;
        final long loadTime;
        CacheEntry(Map<String, String> params, long loadTime) {
            this.params = Collections.unmodifiableMap(params);
            this.loadTime = loadTime;
        }
    }

    /** 分页查询（按去重参数名，默认按修改时间倒序） */
    public PageResult<GlobalParamVO> page(int pageNum, int pageSize, String keyword, String sortField, String sortOrder) {
        if (sortField == null || !ALLOWED_SORT_FIELDS.contains(sortField)) sortField = "update_time";
        if (sortOrder == null || !ALLOWED_SORT_ORDERS.contains(sortOrder.toUpperCase())) sortOrder = "DESC";
        else sortOrder = sortOrder.toUpperCase();
        IPage<String> page = new Page<>(pageNum, pageSize);
        IPage<String> nameResult = mapper.selectDistinctParamNames(page, keyword, sortField, sortOrder);
        List<String> names = nameResult.getRecords();
        if (names.isEmpty()) {
            return PageResult.of(pageNum, pageSize, nameResult.getTotal(), List.of());
        }
        // 批量查询，避免 N+1
        List<GlobalParam> entities = mapper.selectByParamNames(new HashSet<>(names));
        Map<String, GlobalParam> entityMap = entities.stream()
                .collect(Collectors.toMap(GlobalParam::getParamName, Function.identity()));
        // 保持分页返回的顺序
        List<GlobalParamVO> voList = names.stream()
                .map(entityMap::get)
                .filter(Objects::nonNull)
                .map(GlobalParamVO::fromEntity)
                .collect(Collectors.toList());
        return PageResult.of(pageNum, pageSize, nameResult.getTotal(), voList);
    }

    /**
     * 获取当前环境下的所有参数（供参数解析引擎使用）
     * 优先使用 EnvironmentContext 中的环境，否则查询用户默认环境
     */
    public Map<String, String> getAllParams() {
        Long envId = EnvironmentContext.getEnvIdOrDefault();
        if (envId == null) {
            envId = environmentService.getDefaultEnvId();
        }
        return getAllParams(envId);
    }

    /**
     * 获取指定环境下的所有参数 Map（paramName → paramValue）
     * 带 per-env JVM 缓存（60s）
     */
    public Map<String, String> getAllParams(Long envId) {
        String key = cacheKey(envId);
        long now = System.currentTimeMillis();
        CacheEntry entry = envCache.get(key);
        if (entry != null && (now - entry.loadTime) < CACHE_TTL_MS) {
            return new HashMap<>(entry.params);
        }
        // 慢路径：从 DB 加载
        synchronized (getCacheLock(envId)) {
            entry = envCache.get(key);
            if (entry != null && (now - entry.loadTime) < CACHE_TTL_MS) {
                return new HashMap<>(entry.params);
            }
            // 查询 global_param + global_param_value JOIN
            List<GlobalParam> allParams = mapper.selectAll();
            List<GlobalParamValue> values = valueMapper.selectByEnvId(envId);
            Map<Long, String> valueMap = values.stream()
                    .collect(Collectors.toMap(GlobalParamValue::getParamId, GlobalParamValue::getParamValue));

            Map<String, String> map = new HashMap<>();
            for (GlobalParam p : allParams) {
                String val = valueMap.get(p.getId());
                if (val != null) {
                    map.put(p.getParamName(), val);
                }
            }
            envCache.put(key, new CacheEntry(map, System.currentTimeMillis()));
            log.debug("全局参数缓存已刷新: envId={}, 共{}条", envId, map.size());
            return new HashMap<>(map);
        }
    }

    private final Object cacheLock = new Object();
    private Object getCacheLock(Long envId) { return cacheLock; }

    /** 参数缓存 key：环境 + 当前用户 + 是否跳过租户过滤，避免隔离后跨用户/跨上下文串缓存 */
    private static String cacheKey(Long envId) {
        Long userId = TenantContext.getUserId();
        return (userId != null ? userId : 0) + ":" + envId + ":" + (TenantContext.isSkipTenant() ? "skip" : "user");
    }

    /** 全部列表（返回所有参数定义，不含环境值） */
    public List<GlobalParamVO> listAll() {
        return mapper.selectAll().stream().map(GlobalParamVO::fromEntity).collect(Collectors.toList());
    }

    /**
     * 获取全量全局参数实体 Map（id → 实体，Redis 缓存7天）
     * 供 FlowVariableService 批量查全局参数使用
     */
    public Map<Long, GlobalParam> getAllParamMap() {
        Long userId = TenantContext.getUserId();
        String key = RedisCacheUtil.buildKey(GP_CACHE_PREFIX, (userId != null ? userId : 0), "all");
        com.fasterxml.jackson.core.type.TypeReference<List<GlobalParam>> typeRef =
                new com.fasterxml.jackson.core.type.TypeReference<>() {};
        List<GlobalParam> list = redisCacheUtil.get(key, typeRef).orElseGet(() -> {
            List<GlobalParam> all = mapper.selectAll();
            redisCacheUtil.set(key, all, RedisCacheUtil.randomizedTtl());
            return all;
        });
        return list.stream().collect(Collectors.toMap(GlobalParam::getId, Function.identity()));
    }

    /**
     * 获取指定环境下的参数值 Map（paramId → paramValue，Redis 缓存）
     */
    public Map<Long, String> getEnvParamValueMap(Long envId) {
        String key = RedisCacheUtil.buildKey(GP_ENV_VALUE_PREFIX, cacheKey(envId));
        com.fasterxml.jackson.core.type.TypeReference<Map<Long, String>> typeRef =
                new com.fasterxml.jackson.core.type.TypeReference<>() {};
        return redisCacheUtil.get(key, typeRef).orElseGet(() -> {
            List<GlobalParamValue> values = valueMapper.selectByEnvId(envId);
            Map<Long, String> map = values.stream()
                    .collect(Collectors.toMap(GlobalParamValue::getParamId, GlobalParamValue::getParamValue));
            redisCacheUtil.set(key, map, RedisCacheUtil.randomizedTtl());
            return map;
        });
    }

    /**
     * 获取当前环境的参数值 Map（从 EnvironmentContext 读取 envId）
     */
    public Map<Long, String> getEnvParamValueMap() {
        Long envId = EnvironmentContext.getEnvIdOrDefault();
        if (envId == null) {
            envId = environmentService.getDefaultEnvId();
        }
        return getEnvParamValueMap(envId);
    }

    /** 查询某参数名在各环境下的值列表（含环境信息，用于"参数管理"子列表） */
    public List<GlobalParamVO> listValuesByParamName(String paramName) {
        List<GlobalParam> params = mapper.selectAllByParamName(paramName);
        Map<Long, Environment> envMap = environmentService.getEnvMap();
        return params.stream().map(p -> {
            GlobalParamVO vo = GlobalParamVO.fromEntity(p);
            // 加载所有环境值
            List<GlobalParamValue> values = valueMapper.selectByParamId(p.getId());
            List<GlobalParamVO.EnvParamValue> envValues = values.stream().map(v -> {
                Environment env = envMap.get(v.getEnvId());
                String status = "正常";
                String disabledTime = null;
                if (env == null || env.getIsDeleted() == 1) {
                    status = "已停用";
                    disabledTime = env != null && env.getDeletedTime() != null
                            ? env.getDeletedTime().toString() : null;
                }
                return GlobalParamVO.EnvParamValue.builder()
                        .envId(v.getEnvId())
                        .envName(env != null ? env.getName() : "未知环境")
                        .envCode(env != null ? env.getCode() : "")
                        .paramValue(v.getParamValue())
                        .status(status)
                        .disabledTime(disabledTime)
                        .paramValueId(v.getId())
                        .build();
            }).collect(Collectors.toList());
            vo.setEnvValues(envValues);
            return vo;
        }).collect(Collectors.toList());
    }

    /** 统一加 global_ 前缀 */
    private static final String PARAM_PREFIX = "global_";

    private static String resolveParamName(String name) {
        if (name == null || name.isBlank()) return name;
        return name.startsWith(PARAM_PREFIX) ? name : PARAM_PREFIX + name;
    }

    /** 新增 */
    @Transactional
    public GlobalParamVO create(GlobalParamDTO dto) {
        String fullName = resolveParamName(dto.getParamName());
        GlobalParam exist = mapper.selectByParamName(fullName);
        AssertUtils.isNull(exist, ErrorCode.PARAM_NAME_EXISTS, fullName);
        GlobalParam entity = new GlobalParam();
        entity.setParamName(fullName);
        entity.setRemark(dto.getRemark());
        mapper.insert(entity);
        log.info("新增全局参数: {}", fullName);

        // 为用户默认环境创建参数值
        if (dto.getParamValue() != null && !dto.getParamValue().isBlank()) {
            GlobalParamValue value = new GlobalParamValue();
            value.setParamId(entity.getId());
            value.setEnvId(environmentService.getDefaultEnvId());
            value.setParamValue(dto.getParamValue());
            valueMapper.insert(value);
        }
        invalidateCache();
        GlobalParam saved = mapper.selectByParamName(fullName);
        return GlobalParamVO.fromEntity(saved);
    }

    /** 修改参数定义 */
    @Transactional
    public GlobalParamVO update(Long id, GlobalParamDTO dto) {
        GlobalParam entity = mapper.selectById(id);
        AssertUtils.notNull(entity, ErrorCode.PARAM_NOT_FOUND);
        String fullName = resolveParamName(dto.getParamName());
        GlobalParam exist = mapper.selectByParamName(fullName);
        AssertUtils.isTrue(exist == null || exist.getId().equals(id), ErrorCode.PARAM_NAME_EXISTS, fullName);
        entity.setParamName(fullName);
        entity.setRemark(dto.getRemark());
        mapper.update(entity);
        log.info("修改全局参数: id={}, name={}", id, dto.getParamName());
        invalidateCache();
        return GlobalParamVO.fromEntity(entity);
    }

    /** 保存某参数在某环境下的值（UPSERT） */
    @Transactional
    public void saveParamValue(Long paramId, Long envId, String paramValue) {
        AssertUtils.notNull(mapper.selectById(paramId), ErrorCode.PARAM_NOT_FOUND);
        GlobalParamValue exist = valueMapper.selectByParamAndEnv(paramId, envId);
        if (exist != null) {
            exist.setParamValue(paramValue);
            valueMapper.update(exist);
        } else {
            GlobalParamValue value = new GlobalParamValue();
            value.setParamId(paramId);
            value.setEnvId(envId);
            value.setParamValue(paramValue);
            valueMapper.insert(value);
        }
        invalidateCache();
    }

    /** 删除某参数在某环境下的值 */
    @Transactional
    public void deleteParamValue(Long paramId, Long envId) {
        valueMapper.deleteByParamAndEnv(paramId, envId);
        invalidateCache();
    }

    /** 删除参数定义及其所有环境值 */
    @Transactional
    public void delete(Long id) {
        GlobalParam entity = mapper.selectById(id);
        AssertUtils.notNull(entity, ErrorCode.PARAM_NOT_FOUND);
        valueMapper.deleteByParamId(id);
        mapper.deleteById(id);
        log.info("删除全局参数: id={}, name={}", id, entity.getParamName());
        invalidateCache();
    }

    /** 失效全部缓存（JVM + Redis） */
    public void invalidateCache() {
        envCache.clear();
        redisCacheUtil.evictByPattern(RedisCacheUtil.buildKey(GP_CACHE_PREFIX, "*"));
        redisCacheUtil.evictByPattern(RedisCacheUtil.buildKey(GP_ENV_VALUE_PREFIX, "*"));
        log.debug("全局参数缓存已失效（JVM + Redis）");
    }
}
