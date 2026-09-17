package com.arbor.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.arbor.common.AssertUtils;
import com.arbor.common.BusinessException;
import com.arbor.common.ErrorCode;
import com.arbor.common.PageResult;
import com.arbor.common.TenantContext;
import com.arbor.datasource.RedisConnectionManager;
import com.arbor.dto.RedisDatasourceDTO;
import com.arbor.entity.RedisDatasource;
import com.arbor.mapper.RedisDatasourceMapper;
import com.arbor.mapper.RedisKeyPrefixMapper;
import com.arbor.mapper.RedisScriptMapper;
import com.arbor.util.AesUtil;
import com.arbor.util.RedisCacheUtil;
import com.arbor.vo.RedisDatasourceVO;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.sync.RedisCommands;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RedisDatasourceService {
    private final RedisDatasourceMapper mapper;
    private final RedisKeyPrefixMapper redisKeyPrefixMapper;
    private final RedisScriptMapper redisScriptMapper;
    private final AesUtil aesUtil;
    private final RedisCacheUtil redisCacheUtil;
    private final RedisConnectionManager redisConnectionManager;

    /** Redis 数据源缓存 key 前缀（按用户隔离，多租户插件按 user_id 过滤查询结果） */
    static final String REDIS_DS_CACHE_PREFIX = "redis_datasource:user";

    private static final java.util.Set<String> ALLOWED_SORT_FIELDS = java.util.Set.of("create_time", "update_time", "id");
    private static final java.util.Set<String> ALLOWED_SORT_ORDERS = java.util.Set.of("ASC", "DESC");

    /**
     * 带缓存的按 ID 查询（key 按用户隔离，避免多租户过滤被缓存穿透）
     * 未命中缓存时回源数据库并写入缓存
     */
    private RedisDatasource getByIdCached(Long id) {
        Long userId = TenantContext.getUserId();
        String key = RedisCacheUtil.buildKey(REDIS_DS_CACHE_PREFIX, (userId != null ? userId : 0), "id", id);
        return redisCacheUtil.get(key, RedisDatasource.class)
                .orElseGet(() -> {
                    RedisDatasource entity = mapper.selectById(id);
                    if (entity != null) {
                        redisCacheUtil.set(key, entity, RedisCacheUtil.randomizedTtl());
                    }
                    return entity;
                });
    }

    /** 清除某 Redis 数据源在所有用户下的缓存副本（数据改动后调用） */
    private void evictCache(Long id) {
        redisCacheUtil.evictByPattern(RedisCacheUtil.buildKey(REDIS_DS_CACHE_PREFIX, "*", "id", id));
    }

    public PageResult<RedisDatasourceVO> page(int pageNum, int pageSize, String keyword, String sortField, String sortOrder) {
        if (sortField == null || !ALLOWED_SORT_FIELDS.contains(sortField)) sortField = "update_time";
        if (sortOrder == null || !ALLOWED_SORT_ORDERS.contains(sortOrder.toUpperCase())) sortOrder = "DESC";
        else sortOrder = sortOrder.toUpperCase();
        IPage<RedisDatasource> page = new Page<>(pageNum, pageSize);
        IPage<RedisDatasource> result = mapper.selectPage(page, keyword, sortField, sortOrder);
        return PageResult.of(pageNum, pageSize, result.getTotal(), result.getRecords().stream().map(RedisDatasourceVO::fromEntity).collect(Collectors.toList()));
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
                .collect(Collectors.toMap(RedisDatasource::getId, RedisDatasource::getName, (a, b) -> a));
    }

    @Transactional
    public RedisDatasourceVO create(RedisDatasourceDTO dto) {
        var e = new RedisDatasource();
        e.setName(dto.getName()); e.setHost(dto.getHost()); e.setPort(dto.getPort() != null ? dto.getPort() : 6379);
        e.setPassword(dto.getPassword() != null && !dto.getPassword().isBlank() ? aesUtil.encrypt(dto.getPassword()) : null);
        e.setDb(dto.getDb() != null ? dto.getDb() : 0); e.setRemark(dto.getRemark());
        e.setUid(java.util.UUID.randomUUID().toString());
        mapper.insert(e);
        return RedisDatasourceVO.fromEntity(e);
    }

    @Transactional
    public RedisDatasourceVO update(Long id, RedisDatasourceDTO dto) {
        var e = mapper.selectById(id);
        AssertUtils.notNull(e, ErrorCode.REDIS_DATASOURCE_NOT_FOUND);
        e.setName(dto.getName()); e.setHost(dto.getHost()); e.setPort(dto.getPort());
        if (Boolean.TRUE.equals(dto.getModifyPassword()) && dto.getPassword() != null && !dto.getPassword().isBlank()) {
            e.setPassword(aesUtil.encrypt(dto.getPassword()));
        }
        e.setDb(dto.getDb()); e.setRemark(dto.getRemark());
        mapper.update(e);
        // 配置变更后清缓存 + 关闭旧连接，下次执行按新配置重建
        evictCache(id);
        redisConnectionManager.evict(id);
        return RedisDatasourceVO.fromEntity(e);
    }

    @Transactional
    public void delete(Long id) {
        var ds = mapper.selectById(id);
        AssertUtils.notNull(ds, ErrorCode.REDIS_DATASOURCE_NOT_FOUND);
        // 业务前缀与 Redis 配置都挂在数据源下，随数据源级联删除：
        // 数据源没了配置就永远执行不了，留着只会变成管理页上 fullKey 为空、
        // prefix_id 悬空的孤儿行（执行时报 REDIS_PREFIX_NOT_FOUND）。
        // 引用这些配置的流程节点会在执行时报「Redis配置不存在」，与不级联时报「数据源不存在」等价。
        int scripts = redisScriptMapper.deleteByDatasourceId(id);
        redisKeyPrefixMapper.deleteByDatasourceId(id);
        mapper.deleteById(id);
        log.info("删除 Redis 数据源: id={}, name={}, 级联清理配置 {} 条", id, ds.getName(), scripts);
        evictCache(id);
        redisConnectionManager.evict(id);
    }

    /** 测试连接（列表页的「测试」按钮：没有表单，只能按库里已保存的配置测） */
    public boolean testConnection(Long id) {
        RedisDatasource ds = getByIdCached(id);
        AssertUtils.notNull(ds, ErrorCode.REDIS_DATASOURCE_NOT_FOUND);
        return testConnection(ds.getHost(), ds.getPort(), ds.getDb(), decryptStored(ds.getPassword()));
    }

    /**
     * 测试连接（新增/编辑弹窗里**尚未保存**的表单值）
     * <p>
     * host/port/db 一律以表单为准：编辑时改了地址就该按新地址测，回落到库里已保存的旧配置
     * 会让「测试通过」与「保存后实际连不上」同时成立。只有密码是例外 ——
     * 编辑弹窗的密码框默认禁用且不回显，前端手上根本没有旧密码，未开启「修改」时由后端按 {@code dto.id} 回取。
     * </p>
     */
    public boolean testConnectionRaw(RedisDatasourceDTO dto) {
        return testConnection(dto.getHost(), dto.getPort(), dto.getDb(), resolveTestPassword(dto));
    }

    /** 测试连接用的明文密码：填了新密码就用新的，否则回取库里已存的那份（新增态无可回取） */
    private String resolveTestPassword(RedisDatasourceDTO dto) {
        if (dto.getId() == null) return dto.getPassword();
        boolean newPassword = Boolean.TRUE.equals(dto.getModifyPassword())
                && dto.getPassword() != null && !dto.getPassword().isBlank();
        if (newPassword) return dto.getPassword();
        RedisDatasource saved = getByIdCached(dto.getId());
        AssertUtils.notNull(saved, ErrorCode.REDIS_DATASOURCE_NOT_FOUND);
        return decryptStored(saved.getPassword());
    }

    /** 库里存的是 AES 密文（口径同 {@code RedisConnectionManager}）；未设密码的数据源存 null，解密前必须判空 */
    private String decryptStored(String stored) {
        return (stored == null || stored.isBlank()) ? null : aesUtil.decrypt(stored);
    }

    /**
     * 实际发起一次 PING。密码参数约定为**明文**，两种来源（表单新填 / 库里解密）均已在调用前转好，
     * 此处不再「先试解密、失败当明文」—— 那种猜测会让恰好是合法 Base64 的明文密码被误解密而静默用错。
     */
    private boolean testConnection(String host, Integer port, Integer db, String plainPassword) {
        var uri = RedisURI.builder().withHost(host).withPort(port != null ? port : 6379)
                .withDatabase(db != null ? db : 0)
                // 超时必须设在 URI 上：连接建立前命令级 setTimeout 还不生效，否则地址不通时会按默认 60s 干等
                .withTimeout(Duration.ofSeconds(5));
        if (plainPassword != null && !plainPassword.isBlank()) uri.withPassword(plainPassword.toCharArray());
        RedisClient client = null;
        try {
            client = RedisClient.create(uri.build());
            RedisCommands<String, String> cmds = client.connect().sync();
            return "PONG".equals(cmds.ping());
        } catch (Exception e) {
            log.warn("Redis连接测试失败: {}:{}/db{}", host, port, db, e);
            return false;
        } finally {
            // 连不上（地址不通/密码错）正是测试的常见结果，不释放会每次泄漏一组 netty event loop 线程
            if (client != null) client.shutdown();
        }
    }

    /** 获取 Lettuce 连接（实体走缓存，连接由 RedisConnectionManager 长连接复用，调用方不要 close） */
    public RedisCommands<String, String> getConnection(Long id) {
        RedisDatasource ds = getByIdCached(id);
        AssertUtils.notNull(ds, ErrorCode.REDIS_DATASOURCE_NOT_FOUND);
        return redisConnectionManager.getConnection(ds).sync();
    }
}
