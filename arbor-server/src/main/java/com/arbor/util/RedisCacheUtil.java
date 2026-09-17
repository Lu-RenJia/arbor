package com.arbor.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Redis 缓存通用工具
 * <p>
 * 统一管理所有 Redis 缓存的读写和失效，key 规则和业务说明记录在 {@code docs/cache-registry.md}。
 * </p>
 *
 * @author gwl
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisCacheUtil {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 从缓存读取并反序列化为对象
     *
     * @param key  缓存 key
     * @param type 目标类型
     * @return 缓存值，未命中返回 empty
     */
    public <T> Optional<T> get(String key, Class<T> type) {
        try {
            String json = redisTemplate.opsForValue().get(key);
            if (json == null) return Optional.empty();
            return Optional.ofNullable(objectMapper.readValue(json, type));
        } catch (Exception e) {
            log.warn("Redis读取失败: key={}", key, e);
            return Optional.empty();
        }
    }

    /**
     * 从缓存读取并反序列化为泛型对象（如 List、Map）
     */
    public <T> Optional<T> get(String key, TypeReference<T> typeRef) {
        try {
            String json = redisTemplate.opsForValue().get(key);
            if (json == null) return Optional.empty();
            return Optional.ofNullable(objectMapper.readValue(json, typeRef));
        } catch (Exception e) {
            log.warn("Redis读取失败: key={}", key, e);
            return Optional.empty();
        }
    }

    /** 缓存过期随机偏移上限（分钟），最大 60×24 = 1440 分钟 = 1天 */
    private static final int TTL_JITTER_MAX_MINUTES = 60 * 24;
    private static final SecureRandom RNG = new SecureRandom();

    /**
     * 生成带随机抖动的缓存有效期：7天 + [0, 1440] 分钟随机
     * 防止大量缓存同时过期引发缓存雪崩
     */
    public static Duration randomizedTtl() {
        int jitterMinutes = RNG.nextInt(TTL_JITTER_MAX_MINUTES + 1);
        return Duration.ofDays(7).plusMinutes(jitterMinutes);
    }

    /**
     * 写入缓存
     *
     * @param key   缓存 key
     * @param value 值（会被 JSON 序列化）
     * @param ttl   有效期
     */
    public void set(String key, Object value, Duration ttl) {
        try {
            String json = objectMapper.writeValueAsString(value);
            redisTemplate.opsForValue().set(key, json, ttl.toSeconds(), TimeUnit.SECONDS);
            log.debug("Redis写入成功: key={}", key);
        } catch (Exception e) {
            log.error("Redis写入失败: key={}", key, e);
        }
    }

    /**
     * 写入缓存（按秒指定 TTL）
     */
    public void setSeconds(String key, Object value, long ttlSeconds) {
        try {
            String json = objectMapper.writeValueAsString(value);
            if (ttlSeconds > 0) {
                redisTemplate.opsForValue().set(key, json, ttlSeconds, TimeUnit.SECONDS);
            } else {
                redisTemplate.opsForValue().set(key, json);
            }
            log.debug("Redis写入成功: key={}, ttl={}s", key, ttlSeconds);
        } catch (Exception e) {
            log.error("Redis写入失败: key={}", key, e);
        }
    }

    /**
     * 读取原始 JSON 字符串（供 Redis 节点透传）
     */
    public Optional<String> getRaw(String key) {
        try {
            String json = redisTemplate.opsForValue().get(key);
            return Optional.ofNullable(json);
        } catch (Exception e) {
            log.warn("Redis读取失败: key={}", key, e);
            return Optional.empty();
        }
    }

    /**
     * 检查 Redis 是否可用
     */
    public boolean isAvailable() {
        try {
            redisTemplate.opsForValue().get("__health_check__");
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 删除缓存
     */
    public void evict(String key) {
        try {
            redisTemplate.delete(key);
            log.debug("Redis删除成功: key={}", key);
        } catch (Exception e) {
            log.warn("Redis删除失败: key={}", key, e);
        }
    }

    /**
     * 按模式批量删除（如 program_script:*）
     */
    public void evictByPattern(String pattern) {
        try {
            var keys = redisTemplate.keys(pattern);
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
                log.debug("Redis批量删除: pattern={}, 数量={}", pattern, keys.size());
            }
        } catch (Exception e) {
            log.warn("Redis批量删除失败: pattern={}", pattern, e);
        }
    }

    /**
     * 构建缓存 key，用 ":" 拼接多个片段，null 值自动跳过。
     * <p>支持 String、Number 类型，以及 "*" 通配（用于 evictByPattern）。
     * <pre>{@code
     * buildKey("app_market:user", "can_exec", 5, 3, 20)  → "app_market:user:can_exec:5:3:20"
     * buildKey("app_market:user", "can_exec", 5, "*")    → "app_market:user:can_exec:5:*"
     * buildKey("app_market:user", "can_exec", 5, "*", 20) → "app_market:user:can_exec:5:*:20"
     * }</pre>
     */
    public static String buildKey(Object... segments) {
        StringBuilder sb = new StringBuilder();
        for (Object seg : segments) {
            if (seg == null) continue;
            String s = seg.toString();
            if (s.isEmpty()) continue;
            if (!sb.isEmpty()) sb.append(':');
            sb.append(s);
        }
        return sb.toString();
    }
}
