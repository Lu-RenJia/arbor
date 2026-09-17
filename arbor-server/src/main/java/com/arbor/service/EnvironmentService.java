package com.arbor.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.arbor.common.AssertUtils;
import com.arbor.common.BusinessException;
import com.arbor.common.ErrorCode;
import com.arbor.common.PageResult;
import com.arbor.common.TenantContext;
import com.arbor.dto.EnvironmentDTO;
import com.arbor.entity.Environment;
import com.arbor.mapper.EnvironmentMapper;
import com.arbor.util.RedisCacheUtil;
import com.arbor.vo.EnvironmentVO;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 环境配置服务
 *
 * @author gwl
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EnvironmentService {

    private final EnvironmentMapper mapper;
    private final RedisCacheUtil redisCacheUtil;

    /** 缓存 key 前缀 */
    static final String ENV_CACHE_PREFIX = "environment:user:";
    static final String ENV_LIST_SUFFIX = ":list";
    static final String ENV_ID_SUFFIX = ":id:";

    private static final java.util.Set<String> ALLOWED_SORT_FIELDS = java.util.Set.of("create_time", "update_time", "sort_order", "id");
    private static final java.util.Set<String> ALLOWED_SORT_ORDERS = java.util.Set.of("ASC", "DESC");

    @jakarta.annotation.PostConstruct
    void init() {
        redisCacheUtil.evictByPattern(ENV_CACHE_PREFIX + "*" + ENV_LIST_SUFFIX);
    }

    /** 分页查询 */
    public PageResult<EnvironmentVO> page(int pageNum, int pageSize, String keyword, String sortField, String sortOrder) {
        if (sortField == null || !ALLOWED_SORT_FIELDS.contains(sortField)) sortField = "update_time";
        if (sortOrder == null || !ALLOWED_SORT_ORDERS.contains(sortOrder.toUpperCase())) sortOrder = "DESC";
        else sortOrder = sortOrder.toUpperCase();
        IPage<Environment> page = new Page<>(pageNum, pageSize);
        IPage<Environment> result = mapper.selectPage(page, keyword, sortField, sortOrder);
        List<EnvironmentVO> voList = result.getRecords().stream().map(EnvironmentVO::fromEntity).collect(Collectors.toList());
        return PageResult.of(pageNum, pageSize, result.getTotal(), voList);
    }

    /** 查询所有活跃环境（含缓存） */
    public List<EnvironmentVO> listActive() {
        List<Environment> list = getActiveListCached();
        return list.stream().map(EnvironmentVO::fromEntity).collect(Collectors.toList());
    }

    /** 新增 */
    @Transactional
    public EnvironmentVO create(EnvironmentDTO dto) {
        // code 唯一校验
        Environment exist = mapper.selectByCode(dto.getCode());
        AssertUtils.isNull(exist, ErrorCode.ENV_CODE_EXISTS, dto.getCode());
        Environment entity = new Environment();
        entity.setName(dto.getName());
        entity.setCode(dto.getCode());
        entity.setRemark(dto.getRemark());
        entity.setIsDefault(0);
        entity.setIsDeleted(0);
        entity.setSortOrder(dto.getSortOrder() != null ? dto.getSortOrder() : 0);
        mapper.insert(entity);
        log.info("新增环境: name={}, code={}", dto.getName(), dto.getCode());
        evictListCache();
        return EnvironmentVO.fromEntity(entity);
    }

    /** 修改（默认环境禁止改 name/code；开启默认前先清空前旧默认值） */
    @Transactional
    public EnvironmentVO update(Long id, EnvironmentDTO dto) {
        Environment entity = mapper.selectById(id);
        AssertUtils.notNull(entity, ErrorCode.ENV_NOT_FOUND);
        AssertUtils.isTrue(entity.getIsDeleted() != 1, ErrorCode.ENV_NOT_FOUND);
        if (entity.getIsDefault() == 1) {
            AssertUtils.isTrue(entity.getCode().equals(dto.getCode()), ErrorCode.ENV_DEFAULT_CODE_IMMUTABLE);
            AssertUtils.isTrue(entity.getName().equals(dto.getName()), ErrorCode.ENV_DEFAULT_NAME_IMMUTABLE);
        }
        // code 唯一校验（排除自身；租户插件自动过滤当前用户，因此只校验用户内唯一）
        if (!entity.getCode().equals(dto.getCode())) {
            Environment codeExist = mapper.selectByCode(dto.getCode());
            AssertUtils.isNull(codeExist, ErrorCode.ENV_CODE_EXISTS, dto.getCode());
        }
        entity.setName(dto.getName());
        entity.setCode(dto.getCode());
        entity.setRemark(dto.getRemark());
        entity.setSortOrder(dto.getSortOrder() != null ? dto.getSortOrder() : 0);
        mapper.update(entity);
        log.info("修改环境: id={}, name={}", id, dto.getName());
        evictCache(id);
        evictListCache();
        return EnvironmentVO.fromEntity(entity);
    }

    /** 软删除（默认环境禁止） */
    @Transactional
    public void delete(Long id) {
        Environment entity = mapper.selectById(id);
        AssertUtils.notNull(entity, ErrorCode.ENV_NOT_FOUND);
        AssertUtils.isTrue(entity.getIsDeleted() != 1, ErrorCode.ENV_NOT_FOUND);
        AssertUtils.isTrue(entity.getIsDefault() != 1, ErrorCode.ENV_DEFAULT_CANNOT_DELETE);
        mapper.softDelete(id, LocalDateTime.now());
        log.info("删除环境: id={}, name={}", id, entity.getName());
        evictCache(id);
        evictListCache();
    }

    /** 带缓存的按 ID 查询 */
    public Environment getByIdCached(Long id) {
        Long userId = TenantContext.getUserId();
        String key = ENV_CACHE_PREFIX + (userId != null ? userId : 0) + ENV_ID_SUFFIX + id;
        return redisCacheUtil.get(key, Environment.class)
                .orElseGet(() -> {
                    Environment entity = mapper.selectById(id);
                    if (entity != null) {
                        redisCacheUtil.set(key, entity, RedisCacheUtil.randomizedTtl());
                    }
                    return entity;
                });
    }

    /** 查询活跃环境列表（含缓存） */
    private List<Environment> getActiveListCached() {
        Long userId = TenantContext.getUserId();
        String key = ENV_CACHE_PREFIX + userId + ENV_LIST_SUFFIX;
        TypeReference<List<Environment>> typeRef = new TypeReference<>() {};
        return redisCacheUtil.get(key, typeRef)
                .orElseGet(() -> {
                    List<Environment> list = mapper.selectActive(userId);
                    redisCacheUtil.set(key, list, RedisCacheUtil.randomizedTtl());
                    return list;
                });
    }

    /** 获取当前用户的默认环境ID（租户插件自动过滤 user_id） */
    public Long getDefaultEnvId() {
        Environment defaultEnv = mapper.selectDefault();
        if (defaultEnv != null) {
            return defaultEnv.getId();
        }
        // 极端情况：用户无任何环境，回退查第一个活跃环境
        List<Environment> list = mapper.selectActive(TenantContext.getUserId());
        if (list != null && !list.isEmpty()) {
            return list.get(0).getId();
        }
        log.error("当前用户无任何活跃环境，user={}", TenantContext.getUserId());
        throw new BusinessException(ErrorCode.ENV_NOT_FOUND);
    }

    /** 获取所有活跃环境的 Map（id → Environment），含缓存 */
    public java.util.Map<Long, Environment> getEnvMap() {
        List<Environment> list = getActiveListCached();
        java.util.Map<Long, Environment> map = new java.util.HashMap<>();
        for (Environment e : list) {
            map.put(e.getId(), e);
        }
        return Collections.unmodifiableMap(map);
    }

    /** 清除单环境缓存 */
    private void evictCache(Long id) {
        redisCacheUtil.evictByPattern(ENV_CACHE_PREFIX + "*" + ENV_ID_SUFFIX + id);
    }

    /** 清除环境列表缓存 */
    private void evictListCache() {
        redisCacheUtil.evictByPattern(ENV_CACHE_PREFIX + "*" + ENV_LIST_SUFFIX);
    }
}
