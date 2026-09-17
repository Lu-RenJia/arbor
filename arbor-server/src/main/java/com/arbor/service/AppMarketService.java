package com.arbor.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.arbor.common.AssertUtils;
import com.arbor.common.BusinessException;
import com.arbor.common.ErrorCode;
import com.arbor.common.PageResult;
import com.arbor.common.TenantContext;
import com.arbor.entity.AppMarket;
import com.arbor.entity.AppMarketApp;
import com.arbor.entity.Application;
import com.arbor.entity.Flow;
import com.arbor.mapper.AppMarketMapper;
import com.arbor.mapper.ApplicationMapper;
import com.arbor.mapper.FlowMapper;
import com.arbor.util.RedisCacheUtil;
import com.arbor.vo.AppMarketAppVO;
import com.arbor.vo.AppMarketVO;
import com.arbor.vo.AppShareVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AppMarketService {

    private final AppMarketMapper mapper;
    private final ApplicationMapper applicationMapper;
    private final FlowMapper flowMapper;
    private final RedisCacheUtil redisCacheUtil;
    private final EnvironmentService environmentService;
    private final com.arbor.mapper.EnvironmentMapper environmentMapper;
    private final com.arbor.mapper.FlowNodeMapper flowNodeMapper;
    private final com.arbor.mapper.FileResourceMapper fileResourceMapper;

    static final String CACHE_PREFIX = "app_market:user";

    // ===== 广场 CRUD =====

    public PageResult<AppMarketVO> page(int pageNum, int pageSize, String name) {
        IPage<AppMarket> page = new Page<>(pageNum, pageSize);
        IPage<AppMarket> result;
        if (TenantContext.isAdmin()) {
            result = mapper.selectPage(page, name);
        } else {
            result = mapper.selectPageByUserId(page, TenantContext.getUserId(), name);
        }
        List<AppMarketVO> list = result.getRecords().stream().map(AppMarketVO::fromEntity).collect(Collectors.toList());
        return PageResult.of(pageNum, pageSize, result.getTotal(), list);
    }

    @Transactional
    public AppMarketVO create(String name, String remark) {
        AppMarket entity = new AppMarket();
        entity.setName(name);
        entity.setRemark(remark);
        entity.setCreateBy(TenantContext.getUserId());
        mapper.insert(entity);
        evictCache();
        log.info("创建应用广场: {}", name);
        return AppMarketVO.fromEntity(entity);
    }

    @Transactional
    public AppMarketVO update(Long id, String name, String remark) {
        AppMarket entity = mapper.selectById(id);
        AssertUtils.notNull(entity, ErrorCode.MARKET_NOT_FOUND);
        entity.setName(name);
        entity.setRemark(remark);
        entity.setUpdateBy(TenantContext.getUserId());
        mapper.update(entity);
        evictCache();
        return AppMarketVO.fromEntity(entity);
    }

    @Transactional
    public void delete(Long id) {
        AssertUtils.notNull(mapper.selectById(id), ErrorCode.MARKET_NOT_FOUND);
        mapper.deleteById(id);
        // 精准清除该广场下所有权限缓存
        redisCacheUtil.evictByPattern(RedisCacheUtil.buildKey(CACHE_PREFIX, "can_exec", id, "*"));
        evictCache();
    }

    // ===== 人员分配 =====

    public List<Long> getUsers(Long marketId) {
        return mapper.selectUserIdsByMarketId(marketId);
    }

    @Transactional
    public void assignUsers(Long marketId, List<Long> newUserIds) {
        AssertUtils.notNull(mapper.selectById(marketId), ErrorCode.MARKET_NOT_FOUND);
        // 拿旧名单，用于精准清除被移除用户的缓存
        List<Long> oldUserIds = mapper.selectUserIdsByMarketId(marketId);
        mapper.deleteUsersByMarketId(marketId);
        if (newUserIds != null && !newUserIds.isEmpty()) {
            mapper.insertUsers(marketId, newUserIds);
        }
        // 精准清除被移除用户的权限缓存
        if (oldUserIds != null && !oldUserIds.isEmpty()) {
            java.util.Set<Long> newSet = newUserIds != null ? new HashSet<>(newUserIds) : Set.of();
            for (Long uid : oldUserIds) {
                if (!newSet.contains(uid)) {
                    redisCacheUtil.evictByPattern(RedisCacheUtil.buildKey(CACHE_PREFIX, "can_exec", marketId, "*", uid));
                }
            }
        }
        evictCache();
        log.info("广场人员分配: marketId={}, count={}", marketId, newUserIds != null ? newUserIds.size() : 0);
    }

    // ===== 应用共享 =====

    public List<AppMarket> listUserMarkets() {
        // 管理员可共享到所有广场，普通用户只能看到自己被分配到的
        if (TenantContext.isAdmin()) {
            IPage<AppMarket> page = new Page<>(1, 1000);
            return mapper.selectPage(page, null).getRecords();
        }
        return mapper.selectByUserId(TenantContext.getUserId());
    }

    public List<AppMarketAppVO> getMarketApps(Long marketId, String name, String sharedBy) {
        // 权限检查：非管理员必须已被分配到该广场
        AssertUtils.notNull(mapper.selectById(marketId), ErrorCode.MARKET_NOT_FOUND);
        if (!TenantContext.isAdmin() && mapper.isUserInMarket(marketId, TenantContext.getUserId()) == 0) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "你未被分配到该广场");
        }

        // 1. 查广场-应用关联（app_market_app + user，均为白名单表，无租户过滤）
        List<AppMarketAppVO> list = mapper.selectAppsByMarketId(marketId);
        if (list.isEmpty()) return list;

        // 2. 批量加载 application（绕过租户过滤）和 flow
        Set<Long> appIds = list.stream().map(AppMarketAppVO::getAppId).collect(Collectors.toSet());
        Set<Long> flowIds = new HashSet<>();
        Map<Long, Application> appMap;
        if (!appIds.isEmpty()) {
            List<Application> apps = applicationMapper.selectByIds(appIds);
            appMap = apps.stream().collect(Collectors.toMap(Application::getId, a -> a));
            flowIds = apps.stream().map(Application::getFlowId).filter(Objects::nonNull).collect(Collectors.toSet());
        } else {
            appMap = Map.of();
        }
        Map<Long, Flow> flowMap;
        if (!flowIds.isEmpty()) {
            flowMap = flowMapper.selectByIdsIgnoreTenant(flowIds).stream().collect(Collectors.toMap(Flow::getId, f -> f));
        } else {
            flowMap = Map.of();
        }

        // 3. 批量加载环境名称（跳过租户：env 属于应用所有者，非当前用户）
        Set<Long> envIds = list.stream().map(AppMarketAppVO::getEnvId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> envNameMap = new java.util.HashMap<>();
        if (!envIds.isEmpty()) {
            TenantContext.skipTenant(true);
            try {
                for (Long eid : envIds) {
                    var env = environmentMapper.selectById(eid); // 直查 DB，不走缓存
                    if (env != null) envNameMap.put(eid, env.getName());
                }
            } finally {
                TenantContext.skipTenant(false);
            }
        }

        // 4. 批量加载文件上传标志、示例文件标志与参数表单 schema（跳过租户，flow 属于应用所有者）
        Map<Long, Boolean> fileUploadMap = new java.util.HashMap<>();
        Map<Long, Boolean> exampleEnabledMap = new java.util.HashMap<>();
        Map<Long, Object> paramSchemaMap = new java.util.HashMap<>();
        if (!flowIds.isEmpty()) {
            TenantContext.skipTenant(true);
            try {
                var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                for (Long fid : flowIds) {
                    var nodes = flowNodeMapper.selectByFlowId(fid);
                    var startNode = nodes.stream().filter(n -> "START".equals(n.getNodeType())).findFirst().orElse(null);
                    if (startNode != null) {
                        try {
                            var cfg = mapper.readTree(startNode.getConfig());
                            fileUploadMap.put(fid, cfg.has("fileUploadEnabled") && cfg.get("fileUploadEnabled").asBoolean());
                            exampleEnabledMap.put(fid, cfg.has("exampleEnabled") && cfg.get("exampleEnabled").asBoolean());
                            if (cfg.has("paramSchema") && !cfg.get("paramSchema").isNull()) {
                                // 转成纯 Map/List：Spring Boot 4 HTTP 层用 Jackson3，无法正确序列化 Jackson2 的 JsonNode
                                paramSchemaMap.put(fid, mapper.convertValue(cfg.get("paramSchema"), Object.class));
                            }
                        } catch (Exception ignored) {}
                    }
                }
            } finally {
                TenantContext.skipTenant(false);
            }
        }

        // 4.5 批量加载示例文件（跳过租户，文件属于应用所有者）
        Map<Long, com.arbor.entity.FileResource> exampleFileMap = new java.util.HashMap<>();
        Set<Long> exampleFlowIds = exampleEnabledMap.entrySet().stream()
                .filter(Map.Entry::getValue).map(Map.Entry::getKey).collect(Collectors.toSet());
        if (!exampleFlowIds.isEmpty()) {
            TenantContext.skipTenant(true);
            try {
                for (Long fid : exampleFlowIds) {
                    var fr = fileResourceMapper.selectByFlowId(fid);
                    if (fr != null) exampleFileMap.put(fid, fr);
                }
            } finally {
                TenantContext.skipTenant(false);
            }
        }

        // 5. 填充 VO 字段（envId 使用共享记录中的值，不覆盖）
        for (AppMarketAppVO vo : list) {
            Application app = appMap.get(vo.getAppId());
            if (app != null) {
                vo.setAppName(app.getName());
                vo.setFlowId(app.getFlowId());
                vo.setInputParams(app.getInputParams());
                vo.setFileUploadEnabled(fileUploadMap.getOrDefault(app.getFlowId(), false));
                vo.setExampleEnabled(exampleEnabledMap.getOrDefault(app.getFlowId(), false));
                vo.setParamSchema(paramSchemaMap.get(app.getFlowId()));
                var exampleFile = exampleFileMap.get(app.getFlowId());
                if (exampleFile != null) {
                    vo.setExampleFileId(exampleFile.getId());
                    vo.setExampleFileName(exampleFile.getFileName());
                }
                Flow flow = flowMap.get(app.getFlowId());
                if (flow != null) {
                    vo.setFlowName(flow.getName());
                }
            }
            if (vo.getEnvId() != null) {
                vo.setEnvName(envNameMap.getOrDefault(vo.getEnvId(), "未知环境"));
            }
        }

        // 4. 应用名/共享者过滤
        if (name != null && !name.isBlank()) {
            list = list.stream().filter(v -> v.getAppName() != null && v.getAppName().contains(name)).collect(Collectors.toList());
        }
        if (sharedBy != null && !sharedBy.isBlank()) {
            list = list.stream().filter(v -> v.getSharedByName() != null && v.getSharedByName().contains(sharedBy)).collect(Collectors.toList());
        }

        return list;
    }

    @Transactional
    public void shareApp(Long appId, Long marketId, List<Long> envIds) {
        AssertUtils.notNull(mapper.selectById(marketId), ErrorCode.MARKET_NOT_FOUND);
        AssertUtils.isTrue(envIds != null && !envIds.isEmpty(), ErrorCode.PARAM_NOT_FOUND, "envIds");
        // 先删旧记录，再批量插入（支持环境多选）
        mapper.deleteAppByMarketAndApp(marketId, appId);
        Long sharedBy = TenantContext.getUserId();
        for (Long envId : envIds) {
            AppMarketApp entity = new AppMarketApp();
            entity.setMarketId(marketId);
            entity.setAppId(appId);
            entity.setEnvId(envId);
            entity.setSharedBy(sharedBy);
            mapper.insertApp(entity);
        }
        evictCache();
        log.info("应用共享: appId={}, marketId={}, envIds={}", appId, marketId, envIds);
    }

    @Transactional
    public void unshareApp(Long appId, Long marketId) {
        // 授权校验：仅共享者本人或管理员可取消共享
        // （此前只校验 app:my:list 权限，任何登录用户都能取消任意应用的共享，属越权漏洞）
        Long sharedBy = mapper.selectSharedBy(marketId, appId);
        if (sharedBy == null) {
            // 无共享记录，幂等返回（可能已被移除）
            return;
        }
        Long caller = TenantContext.getUserId();
        if (!TenantContext.isAdmin() && !sharedBy.equals(caller)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只有共享者本人或管理员可以取消共享");
        }
        mapper.deleteAppByMarketAndApp(marketId, appId);
        // 精准清除该应用在该广场下所有用户的权限缓存
        redisCacheUtil.evictByPattern(RedisCacheUtil.buildKey(CACHE_PREFIX, "can_exec", marketId, appId, "*"));
        evictCache();
        log.info("取消共享: appId={}, marketId={}, operator={}", appId, marketId, caller);
    }

    /** 查询某应用被共享到的所有广场明细（扁平：同广场多环境多行，前端按 marketId 分组） */
    public List<AppShareVO> getSharesByApp(Long appId) {
        return mapper.selectSharesByAppId(appId);
    }

    // ===== 权限检查 =====

    /**
     * 检查用户是否有权执行某个应用（带缓存，TTL=7天+随机抖动）
     * <p>Key 层级：{marketId}:{appId}:{userId}，从左到右从大到小，失效时按层截断。
     *
     * @param marketId 广场 ID，调用方必须传入（MyApps 走所有者校验不会走到这里）
     */
    public boolean canExecuteApp(Long appId, Long userId, Long marketId) {
        if (userId == null) return false;
        // 调用方必须传 marketId；不传说明是非法直接 API 调用，直接拒绝
        if (marketId == null) return false;
        String cacheKey = RedisCacheUtil.buildKey(CACHE_PREFIX, "can_exec", marketId, appId, userId);

        Boolean cached = redisCacheUtil.get(cacheKey, Boolean.class).orElse(null);
        if (cached != null) return cached;

        // 管理员可执行任意应用
        if (TenantContext.isAdmin()) {
            redisCacheUtil.set(cacheKey, true, RedisCacheUtil.randomizedTtl());
            return true;
        }

        // 单条 JOIN 验证"用户属于广场 AND 应用共享到该广场"
        boolean allowed = mapper.isUserAndAppInMarket(marketId, userId, appId) > 0;
        redisCacheUtil.set(cacheKey, allowed, RedisCacheUtil.randomizedTtl());
        return allowed;
    }

    /** 清除权限缓存（人员分配 / 应用共享变更时调用） */
    public void evictCanExecCache(Long appId, Long userId) {
        if (userId != null && appId != null) {
            redisCacheUtil.evictByPattern(RedisCacheUtil.buildKey(CACHE_PREFIX, "can_exec", "*", appId, userId));
        }
    }

    private void evictCache() {
        redisCacheUtil.evictByPattern(RedisCacheUtil.buildKey(CACHE_PREFIX, "*"));
    }
}
