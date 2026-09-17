package com.arbor.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.arbor.common.AssertUtils;
import com.arbor.common.BusinessException;
import com.arbor.common.ErrorCode;
import com.arbor.common.PageResult;
import com.arbor.common.TenantContext;
import com.arbor.dto.RedisScriptDTO;
import com.arbor.engine.ExecutionLogRecorder;
import com.arbor.engine.FlowEngine;
import com.arbor.engine.NodeResult;
import com.arbor.engine.ParamResolver;
import com.arbor.entity.RedisKeyPrefix;
import com.arbor.entity.RedisScript;
import com.arbor.mapper.RedisScriptMapper;
import com.arbor.util.RedisCacheUtil;
import com.arbor.vo.RedisScriptVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.lettuce.core.api.sync.RedisCommands;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Redis 脚本配置服务：CRUD + 命令执行（含 GET&amp;SET 组合命令）
 * <p>
 * key 规则（D5）：最终落地 key = {@code {业务前缀}:{配置里填的 redisKey}}，
 * 统一由 {@link #buildBusinessKey} 产出；系统不再追加 env / user / flow 任何前缀（D2/D4）。
 * </p>
 *
 * @author gwl
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RedisScriptService {

    /** 组合命令：先 GET，流程中未命中走分支后回填 SET；配置页测试只执行 GET */
    public static final String CMD_GET_SET = "GET&SET";
    /** 支持的命令白名单 */
    private static final Set<String> SUPPORTED_COMMANDS = Set.of("GET", "SET", "DEL", "EXPIRE", "TTL", "EXISTS", CMD_GET_SET);
    /** TTL 单位白名单 */
    private static final Set<String> TTL_UNITS = Set.of("SECONDS", "MINUTES", "HOURS", "DAYS");
    /** TTL 默认单位（存量数据兼容：ttl 按秒） */
    private static final String DEFAULT_TTL_UNIT = "SECONDS";

    private final RedisScriptMapper mapper;
    private final RedisDatasourceService redisDatasourceService;
    private final RedisKeyPrefixService redisKeyPrefixService;
    private final GlobalParamService globalParamService;
    private final ParamResolver paramResolver;
    private final ObjectMapper objectMapper;
    private final RedisCacheUtil redisCacheUtil;
    private final ExecutionLogRecorder executionLogRecorder;

    /** Redis 脚本配置缓存 key 前缀（按用户隔离，多租户插件按 user_id 过滤查询结果） */
    static final String REDIS_SCRIPT_CACHE_PREFIX = "redis_script:user";

    private static final java.util.Set<String> ALLOWED_SORT_FIELDS = java.util.Set.of("create_time", "update_time", "id");
    private static final java.util.Set<String> ALLOWED_SORT_ORDERS = java.util.Set.of("ASC", "DESC");

    /**
     * 带缓存的按 ID 查询（key 按用户隔离，避免多租户过滤被缓存穿透）
     * 未命中缓存时回源数据库并写入缓存
     */
    private RedisScript getByIdCached(Long id) {
        Long userId = TenantContext.getUserId();
        String key = RedisCacheUtil.buildKey(REDIS_SCRIPT_CACHE_PREFIX, (userId != null ? userId : 0), "id", id);
        return redisCacheUtil.get(key, RedisScript.class)
                .orElseGet(() -> {
                    RedisScript entity = mapper.selectById(id);
                    if (entity != null) {
                        redisCacheUtil.set(key, entity, RedisCacheUtil.randomizedTtl());
                    }
                    return entity;
                });
    }

    /** 清除某脚本配置在所有用户下的缓存副本（数据改动后调用） */
    private void evictCache(Long id) {
        redisCacheUtil.evictByPattern(RedisCacheUtil.buildKey(REDIS_SCRIPT_CACHE_PREFIX, "*", "id", id));
    }

    public PageResult<RedisScriptVO> page(int pageNum, int pageSize, String keyword, String sortField, String sortOrder) {
        if (sortField == null || !ALLOWED_SORT_FIELDS.contains(sortField)) sortField = "update_time";
        if (sortOrder == null || !ALLOWED_SORT_ORDERS.contains(sortOrder.toUpperCase())) sortOrder = "DESC";
        else sortOrder = sortOrder.toUpperCase();
        IPage<RedisScript> page = new Page<>(pageNum, pageSize);
        IPage<RedisScript> result = mapper.selectPage(page, keyword, sortField, sortOrder);
        List<RedisScript> records = result.getRecords();
        // 一次 IN 查询取回本页涉及的前缀值，避免逐行查库（N+1）
        Map<Long, String> prefixValues = redisKeyPrefixService.mapPrefixValues(
                records.stream().map(RedisScript::getPrefixId).collect(Collectors.toList()));
        // 一次 IN 查询取回本页涉及的数据源名称，避免逐行查库（N+1）
        Map<Long, String> datasourceNames = redisDatasourceService.mapDatasourceNames(
                records.stream().map(RedisScript::getDatasourceId).collect(Collectors.toList()));
        return PageResult.of(pageNum, pageSize, result.getTotal(),
                records.stream().map(e -> toVO(e, prefixValues, datasourceNames)).collect(Collectors.toList()));
    }

    /**
     * 某流程的内联配置列表（设计器右侧属性面板用），批量转 VO 并回填前缀值与完整 key。
     * <p>
     * 走与 {@link #page} 同一套回填逻辑，是为了让「内联模式摘要卡」与「全局管理页列表」
     * 显示同一个完整 key —— 否则设计器里只能看到裸 redisKey，用户无从判断实际会写到哪里。
     * </p>
     */
    public List<RedisScriptVO> listVOByFlow(Long flowId) {
        List<RedisScript> list = mapper.selectByFlowId(flowId);
        if (list.isEmpty()) return List.of();
        Map<Long, String> prefixValues = redisKeyPrefixService.mapPrefixValues(
                list.stream().map(RedisScript::getPrefixId).collect(Collectors.toList()));
        Map<Long, String> datasourceNames = redisDatasourceService.mapDatasourceNames(
                list.stream().map(RedisScript::getDatasourceId).collect(Collectors.toList()));
        return list.stream().map(e -> toVO(e, prefixValues, datasourceNames)).collect(Collectors.toList());
    }

    @Transactional
    public RedisScriptVO create(RedisScriptDTO dto) {
        validateDto(dto);
        var e = new RedisScript();
        applyDto(e, dto);
        e.setFlowId(dto.getFlowId());
        e.setUid(java.util.UUID.randomUUID().toString());
        mapper.insert(e);
        return toVO(e);
    }

    @Transactional
    public RedisScriptVO update(Long id, RedisScriptDTO dto) {
        validateDto(dto);
        var e = mapper.selectById(id);
        AssertUtils.notNull(e, ErrorCode.REDIS_SCRIPT_NOT_FOUND);
        applyDto(e, dto);
        mapper.update(e);
        evictCache(id);
        return toVO(e);
    }

    @Transactional
    public void delete(Long id) {
        AssertUtils.notNull(mapper.selectById(id), ErrorCode.REDIS_SCRIPT_NOT_FOUND);
        mapper.deleteById(id);
        evictCache(id);
    }

    /** DTO 校验并规范化：命令白名单、TTL 单位白名单（空默认秒）、GET&amp;SET 必须 ttl&gt;0 且不允许配置 Value、业务前缀必选且属于所选数据源 */
    private void validateDto(RedisScriptDTO dto) {
        String cmd = dto.getCommand() != null ? dto.getCommand().toUpperCase() : "";
        AssertUtils.isTrue(SUPPORTED_COMMANDS.contains(cmd), ErrorCode.REDIS_CMD_UNSUPPORTED, dto.getCommand());
        dto.setCommand(cmd);
        String unit = dto.getTtlUnit() == null || dto.getTtlUnit().isBlank() ? DEFAULT_TTL_UNIT : dto.getTtlUnit().toUpperCase();
        AssertUtils.isTrue(TTL_UNITS.contains(unit), ErrorCode.REDIS_TTL_UNIT_UNSUPPORTED, dto.getTtlUnit());
        dto.setTtlUnit(unit);
        if (CMD_GET_SET.equals(cmd)) {
            AssertUtils.isTrue(dto.getTtl() != null && dto.getTtl() > 0, ErrorCode.REDIS_GETSET_NO_TTL_VAL);
            // GET&SET 的写入值来自流程分支结果，不允许配置固定 Value
            dto.setRedisValue(null);
        }
        // 业务前缀必选（D7）：前缀承担 key 的层级管理与溯源职责，缺了就拼不出 key
        AssertUtils.notNull(dto.getPrefixId(), ErrorCode.REDIS_SCRIPT_NO_PREFIX);
        RedisKeyPrefix prefix = redisKeyPrefixService.getById(dto.getPrefixId());
        AssertUtils.notNull(prefix, ErrorCode.REDIS_PREFIX_NOT_FOUND);
        // 前缀必须属于所选数据源：跨数据源引用会让「某前缀被谁使用」的统计失真
        AssertUtils.isTrue(java.util.Objects.equals(prefix.getDatasourceId(), dto.getDatasourceId()),
                ErrorCode.REDIS_PREFIX_DS_MISMATCH, prefix.getPrefix());
    }

    private void applyDto(RedisScript e, RedisScriptDTO dto) {
        e.setName(dto.getName()); e.setDatasourceId(dto.getDatasourceId()); e.setPrefixId(dto.getPrefixId());
        e.setCommand(dto.getCommand());
        e.setRedisKey(dto.getRedisKey()); e.setRedisValue(dto.getRedisValue());
        e.setTtl(dto.getTtl()); e.setTtlUnit(dto.getTtlUnit()); e.setRemark(dto.getRemark());
        // 故意不设 flowId：归属只由 create（内联）与 InlineResourceController 的 promote/demote 变更。
        // 编辑表单不传 flowId，在此覆盖会把内联资源静默改成全局（与 DbScriptService 同口径）
    }

    /** 实体转 VO（新建/修改后回显，只涉及一个前缀） */
    private RedisScriptVO toVO(RedisScript e) {
        Map<Long, String> prefixValues = redisKeyPrefixService.mapPrefixValues(java.util.Collections.singletonList(e.getPrefixId()));
        Map<Long, String> datasourceNames = redisDatasourceService.mapDatasourceNames(java.util.Collections.singletonList(e.getDatasourceId()));
        return toVO(e, prefixValues, datasourceNames);
    }

    /** 实体转 VO 并回填前缀值、数据源名称与完整 key 预览（前缀值/数据源名由调用方批量查好传入，避免 N+1） */
    private RedisScriptVO toVO(RedisScript e, Map<Long, String> prefixValues, Map<Long, String> datasourceNames) {
        RedisScriptVO vo = RedisScriptVO.fromEntity(e);
        String prefix = prefixValues.get(e.getPrefixId());
        vo.setPrefixValue(prefix);
        vo.setFullKey(prefix != null ? buildBusinessKey(prefix, e.getRedisKey()) : null);
        vo.setDatasourceName(datasourceNames.get(e.getDatasourceId()));
        return vo;
    }

    /** TTL 换算为秒；单位为空按秒（存量兼容） */
    public static long ttlToSeconds(Integer ttl, String ttlUnit) {
        if (ttl == null) return 0L;
        String unit = ttlUnit != null ? ttlUnit.toUpperCase() : DEFAULT_TTL_UNIT;
        return switch (unit) {
            case "MINUTES" -> ttl * 60L;
            case "HOURS" -> ttl * 3600L;
            case "DAYS" -> ttl * 86400L;
            default -> ttl.longValue();
        };
    }

    /** 按 id 查配置行（走缓存；不存在返回 null，由调用方决定报错方式） */
    public RedisScript getScript(Long id) {
        return id != null ? getByIdCached(id) : null;
    }

    /** 测试执行（用全局参数解析 ${}） */
    public Object testExecute(Long id) {
        RedisScript script = getByIdCached(id);
        AssertUtils.notNull(script, ErrorCode.REDIS_SCRIPT_NOT_FOUND);
        String resolvedKey = DatasourceService.resolve(script.getRedisKey(), globalParamService.getAllParams());
        String resolvedValue = script.getRedisValue() != null ? DatasourceService.resolve(script.getRedisValue(), globalParamService.getAllParams()) : null;
        String fullKey = buildBusinessKey(script, resolvedKey);
        long start = System.currentTimeMillis();
        try {
            Object result = executeResolved(script, fullKey, resolvedValue);
            executionLogRecorder.save(null, script.getName(), FlowEngine.SOURCE_SCRIPT_TEST,
                    fullKey, "SUCCESS", System.currentTimeMillis() - start, null);
            return result;
        } catch (Exception e) {
            executionLogRecorder.save(null, script.getName(), FlowEngine.SOURCE_SCRIPT_TEST,
                    fullKey, "FAIL", System.currentTimeMillis() - start, e.getMessage());
            throw e;
        }
    }

    /** 草稿测试：不落库，直接按表单内容执行（GET&amp;SET 只执行 GET） */
    public Object testExecuteDraft(RedisScriptDTO dto) {
        validateDto(dto);
        var script = new RedisScript();
        applyDto(script, dto);
        String resolvedKey = DatasourceService.resolve(script.getRedisKey(), globalParamService.getAllParams());
        String resolvedValue = script.getRedisValue() != null ? DatasourceService.resolve(script.getRedisValue(), globalParamService.getAllParams()) : null;
        String fullKey = buildBusinessKey(script, resolvedKey);
        long start = System.currentTimeMillis();
        try {
            Object result = executeResolved(script, fullKey, resolvedValue);
            executionLogRecorder.save(null, dto.getName(), FlowEngine.SOURCE_SCRIPT_TEST,
                    fullKey, "SUCCESS", System.currentTimeMillis() - start, null);
            return result;
        } catch (Exception e) {
            executionLogRecorder.save(null, dto.getName(), FlowEngine.SOURCE_SCRIPT_TEST,
                    fullKey, "FAIL", System.currentTimeMillis() - start, e.getMessage());
            throw e;
        }
    }

    /** 解析配置 key 中的 ${}（全局参数 + 前置节点结果） */
    public String resolveScriptKey(RedisScript script, Map<String, String> globalParams, Map<String, NodeResult> previousResults) {
        return paramResolver.resolve(script.getRedisKey(), globalParams, previousResults);
    }

    /** 解析配置 value 中的 ${}（无 value 返回 null） */
    public String resolveScriptValue(RedisScript script, Map<String, String> globalParams, Map<String, NodeResult> previousResults) {
        return script.getRedisValue() != null ? paramResolver.resolve(script.getRedisValue(), globalParams, previousResults) : null;
    }

    /**
     * 按已拼好的完整业务 key 执行配置命令。GET&amp;SET 在此处只执行 GET
     * （流程中未命中后的 SET 由引擎走 {@link #setCacheValue} 回填）。
     * <p>
     * {@code fullKey} 必须由 {@link #buildBusinessKey} 产出，本方法不再追加任何系统前缀
     * （原先的 {@code env:{envId}:} 已废弃，环境隔离改由「不同环境配不同数据源」实现，D2）。
     * </p>
     */
    public Object executeResolved(RedisScript script, String fullKey, String value) {
        // 连接由 RedisConnectionManager 长连接复用，此处不 close
        RedisCommands<String, String> redis = openConnection(script);
        String cmd = script.getCommand().toUpperCase();
        return switch (cmd) {
            case "GET", CMD_GET_SET -> redis.get(fullKey);
            case "SET" -> {
                redis.set(fullKey, value != null ? value : "");
                long ttlSeconds = ttlToSeconds(script.getTtl(), script.getTtlUnit());
                if (ttlSeconds > 0) redis.expire(fullKey, ttlSeconds);
                yield "OK";
            }
            case "DEL" -> redis.del(fullKey);
            case "EXPIRE" -> redis.expire(fullKey, ttlToSeconds(script.getTtl(), script.getTtlUnit()));
            case "TTL" -> redis.ttl(fullKey);
            case "EXISTS" -> redis.exists(fullKey);
            default -> throw new BusinessException(ErrorCode.REDIS_CMD_UNSUPPORTED, cmd);
        };
    }

    /**
     * GET&amp;SET 分支回填：把分支结果写入缓存（JSON 序列化，按配置 TTL 换算秒设置过期）。
     *
     * @param script  配置行（提供数据源与 TTL）
     * @param fullKey 完整业务 key，必须由 {@link #buildBusinessKey} 产出（与 GET 侧同规则，保证命中）
     * @param value   要缓存的分支结果
     */
    public void setCacheValue(RedisScript script, String fullKey, Object value) throws Exception {
        String json = value instanceof String s ? s : objectMapper.writeValueAsString(value);
        // 连接由 RedisConnectionManager 长连接复用，此处不 close
        RedisCommands<String, String> redis = openConnection(script);
        redis.set(fullKey, json);
        long ttlSeconds = ttlToSeconds(script.getTtl(), script.getTtlUnit());
        if (ttlSeconds > 0) redis.expire(fullKey, ttlSeconds);
        log.info("Redis GET&SET 回填缓存: key={}, ttl={}s", fullKey, ttlSeconds);
    }

    /**
     * key 拼接规则的唯一实现（纯函数、无副作用）：{@code {业务前缀}:{用户填的 key}}。
     * <p>
     * 静态版供列表回显与统计页（{@code RedisKeyPrefixService.getUsage}）共用，
     * 保证「看到的 key」与「执行期落地的 key」永远是同一条规则，不会两边各自漂移。
     * </p>
     */
    public static String buildBusinessKey(String prefix, String resolvedKey) {
        return RedisCacheUtil.buildKey(prefix, resolvedKey);
    }

    /**
     * 执行期唯一 key 出口：取配置行绑定的业务前缀拼完整 key（D5）。
     * <p>
     * GET 与 GET&amp;SET 回填两条路径都必须走这里，否则两边 key 不一致会导致缓存永远未命中。
     * 前缀实时查库不加缓存，保证前缀改名后立即生效。原先系统强加的
     * {@code env:{envId}:flow:redis:user:{uid}:flow:{flowId}:} 已彻底移除（D2/D4）。
     * </p>
     */
    public String buildBusinessKey(RedisScript script, String resolvedKey) {
        return buildBusinessKey(redisKeyPrefixService.getPrefixValue(script.getPrefixId()), resolvedKey);
    }

    private RedisCommands<String, String> openConnection(RedisScript script) {
        AssertUtils.notNull(script.getDatasourceId(), ErrorCode.REDIS_NO_DATASOURCE);
        return redisDatasourceService.getConnection(script.getDatasourceId());
    }
}
