package com.arbor.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * Redis 分布式锁（SET NX EX + Lua 比对删除）
 * <p>
 * 用于多实例部署下定时任务的互斥抢占：同一时刻只有一个实例能执行某个流程的定时触发。
 * Redis 不可用时降级为"视为获锁成功"（退回单机模式，由进程内 runningMap 防堆积），
 * 保证 Redis 故障不阻塞业务。
 * </p>
 *
 * @author gwl
 */
@Slf4j
@Component
public class RedisLockUtil {

    /** Lua 脚本：仅当锁值匹配时删除，防止误删其他实例持有的锁 */
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);

    private final StringRedisTemplate redisTemplate;

    public RedisLockUtil(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /** 生成唯一锁值（实例+随机），解锁时比对，防止误删他人锁 */
    public static String newLockValue() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 尝试加锁（非阻塞）
     *
     * @param key   锁 key
     * @param value 锁值（{@link #newLockValue()}），解锁时必须传同一值
     * @param ttl   锁有效期（持锁进程崩溃后自动释放，避免死锁）
     * @return true=获锁成功；Redis 不可用时降级返回 true（单机模式）
     */
    public boolean tryLock(String key, String value, Duration ttl) {
        try {
            Boolean ok = redisTemplate.opsForValue().setIfAbsent(key, value, ttl);
            return Boolean.TRUE.equals(ok);
        } catch (Exception e) {
            log.warn("Redis 分布式锁获取异常，降级为单机模式: key={}, {}", key, e.getMessage());
            return true;
        }
    }

    /**
     * 解锁（Lua 原子比对删除，仅当锁值匹配才删除）
     *
     * @param key   锁 key
     * @param value 加锁时使用的锁值
     */
    public void unlock(String key, String value) {
        try {
            redisTemplate.execute(UNLOCK_SCRIPT, List.of(key), value);
        } catch (Exception e) {
            log.warn("Redis 分布式锁释放失败（将等待 TTL 自动过期）: key={}, {}", key, e.getMessage());
        }
    }
}
