package com.arbor.datasource;

import com.arbor.entity.RedisDatasource;
import com.arbor.util.AesUtil;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Redis 数据源连接管理器 — 按 redis_datasource id 缓存 Lettuce 长连接复用
 * <p>
 * 与 {@link DynamicDataSourceManager}（JDBC 连接池）对应的 Redis 侧设施：
 * 首次调用建连接入缓存，后续复用同一 StatefulRedisConnection（线程安全、默认自动重连），
 * 配置变更/删除时调用 {@link #evict} 关闭并驱逐。
 * </p>
 *
 * @author gwl
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisConnectionManager {

    private final AesUtil aesUtil;

    /** RedisClient 缓存，key = redis_datasource id（shutdown 时需要，与连接同生命周期） */
    private final ConcurrentHashMap<Long, RedisClient> clientCache = new ConcurrentHashMap<>();
    /** 长连接缓存，key = redis_datasource id */
    private final ConcurrentHashMap<Long, StatefulRedisConnection<String, String>> connCache = new ConcurrentHashMap<>();

    /**
     * 获取指定 Redis 数据源的长连接（首次调用建立，后续复用；连接断开则重建兜底）
     *
     * @param ds Redis 数据源实体
     * @return 可复用的 Lettuce 连接
     */
    public StatefulRedisConnection<String, String> getConnection(RedisDatasource ds) {
        StatefulRedisConnection<String, String> conn = connCache.computeIfAbsent(ds.getId(), k -> {
            RedisClient client = RedisClient.create(buildUri(ds));
            clientCache.put(k, client);
            log.info("创建 Redis 连接: dsId={}, {}:{}/{}", ds.getId(), ds.getHost(), ds.getPort(), ds.getDb());
            return client.connect(); // 长连接复用，天然线程安全
        });
        if (!conn.isOpen()) {
            // 连接被外部关闭（驱逐后残留引用等），移除重建兜底；Lettuce 默认自动重连，此分支极少触发
            evict(ds.getId());
            return getConnection(ds);
        }
        return conn;
    }

    /**
     * 驱逐指定数据源的连接（配置变更或删除时调用）
     *
     * @param datasourceId Redis 数据源ID
     */
    public void evict(Long datasourceId) {
        StatefulRedisConnection<String, String> conn = connCache.remove(datasourceId);
        if (conn != null && conn.isOpen()) {
            conn.close();
        }
        RedisClient client = clientCache.remove(datasourceId);
        if (client != null) {
            client.shutdown();
        }
        if (conn != null || client != null) {
            log.info("已关闭 Redis 连接: dsId={}", datasourceId);
        }
    }

    /** 应用关闭时释放所有连接 */
    @PreDestroy
    public void destroy() {
        connCache.forEach((id, conn) -> {
            if (conn.isOpen()) {
                conn.close();
            }
            log.info("应用关闭，释放 Redis 连接: dsId={}", id);
        });
        connCache.clear();
        clientCache.forEach((id, client) -> client.shutdown());
        clientCache.clear();
    }

    /** 构建连接 URI（密码为 AES 密文，此处解密） */
    private RedisURI buildUri(RedisDatasource ds) {
        var uri = RedisURI.builder().withHost(ds.getHost()).withPort(ds.getPort())
                .withDatabase(ds.getDb() != null ? ds.getDb() : 0);
        if (ds.getPassword() != null && !ds.getPassword().isBlank()) {
            uri.withPassword(aesUtil.decrypt(ds.getPassword()).toCharArray());
        }
        return uri.build();
    }
}
