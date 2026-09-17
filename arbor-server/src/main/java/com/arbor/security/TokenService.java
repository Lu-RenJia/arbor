package com.arbor.security;

import com.arbor.util.RedisCacheUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Redis Token 管理服务
 * <p>
 * JWT 仅含 UUID，真正的用户信息存 Redis。删除 Redis key 即实现即时踢出/登出。
 * 每次请求自动续期：剩余时间不足 refreshThreshold 分钟时刷新 TTL。
 * </p>
 *
 * @author gwl
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenService {

    private final RedisCacheUtil redisCacheUtil;

    /** Redis key 前缀 */
    static final String TOKEN_PREFIX = "login_tokens:";
    static final String USERID_PREFIX = "login_userid:";

    /** 每分钟毫秒数 */
    private static final long MILLIS_PER_MINUTE = 60 * 1000L;
    /** admin 登录有效期（分钟）：24小时 */
    private static final int ADMIN_EXPIRE_MINUTES = 1440;

    @Value("${arbor.token.expire-time:30}")
    private int expireTimeMinutes;

    @Value("${arbor.token.refresh-threshold:20}")
    private int refreshThresholdMinutes;

    // ===================== 公开 API =====================

    /**
     * 创建登录 token：生成 UUID → LoginUser 存 Redis → 返回 UUID
     */
    public String createToken(LoginUser loginUser) {
        String token = UUID.randomUUID().toString().replace("-", "");
        loginUser.setToken(token);
        refreshToken(loginUser);
        return token;
    }

    /**
     * 刷新 token：更新 loginTime/expireTime，重新写入 Redis。
     * 同时处理单设备登录：如果该用户已有旧 token，踢除旧会话。
     */
    public void refreshToken(LoginUser loginUser) {
        long now = System.currentTimeMillis();
        int ttlMinutes = isAdmin(loginUser) ? ADMIN_EXPIRE_MINUTES : expireTimeMinutes;
        loginUser.setLoginTime(now);
        loginUser.setExpireTime(now + (long) ttlMinutes * MILLIS_PER_MINUTE);

        String userKey = tokenKey(loginUser.getToken());
        String userIdKey = userIdTokenKey(loginUser.getUserId());

        // 单设备登录：非 admin 检查是否已有活跃 token，有则踢除旧会话
        if (!isAdmin(loginUser)) {
            String oldUserKey = redisCacheUtil.get(userIdKey, String.class).orElse(null);
            if (oldUserKey != null && !oldUserKey.equals(userKey)) {
                redisCacheUtil.evict(oldUserKey);
                log.info("单设备登录：已踢除旧会话 userId={}", loginUser.getUserId());
            }
        }

        // 写入当前 token → 用户信息
        redisCacheUtil.setSeconds(userKey, loginUser, (long) ttlMinutes * 60);
        // 写入 userId → tokenKey 映射（非admin用于单设备踢除；admin用于权限刷新）
        redisCacheUtil.setSeconds(userIdKey, userKey, (long) ttlMinutes * 60);
        log.debug("Token已刷新: userId={}, isAdmin={}, expireIn={}min", loginUser.getUserId(), isAdmin(loginUser), ttlMinutes);
    }

    private static boolean isAdmin(LoginUser u) {
        return u.getUserId() != null && u.getUserId() == 1L;
    }

    /**
     * 验证 token 是否需要续期：剩余时间 ≤ refreshThreshold 时刷新
     */
    public void verifyToken(LoginUser loginUser) {
        long remaining = loginUser.getExpireTime() - System.currentTimeMillis();
        if (remaining <= (long) refreshThresholdMinutes * MILLIS_PER_MINUTE) {
            refreshToken(loginUser);
        }
    }

    /**
     * 从 Redis 获取 LoginUser，不存在返回 null
     */
    public LoginUser getLoginUser(String token) {
        return redisCacheUtil.get(tokenKey(token), LoginUser.class).orElse(null);
    }

    /**
     * 刷新指定用户的权限缓存（角色/菜单变更后调用）。
     * 如果用户已登录，重新查 DB 获取最新权限并写回 Redis，无需重新登录。
     */
    public void refreshUserPerms(Long userId, List<String> newPermissions) {
        String tokenKeyVal = redisCacheUtil.get(userIdTokenKey(userId), String.class).orElse(null);
        if (tokenKeyVal == null) return; // 未登录
        LoginUser loginUser = redisCacheUtil.get(tokenKeyVal, LoginUser.class).orElse(null);
        if (loginUser == null) return;
        loginUser.setPermissions(newPermissions);
        long remainingSec = (loginUser.getExpireTime() - System.currentTimeMillis()) / 1000;
        redisCacheUtil.setSeconds(tokenKeyVal, loginUser, Math.max(remainingSec, 60));
        log.info("权限缓存已刷新: userId={}, perms={}", userId, newPermissions.size());
    }

    /**
     * 删除登录 token（登出/踢出），同时清理 userId 映射
     */
    public void delLoginUser(String token) {
        if (token == null || token.isEmpty()) return;
        LoginUser loginUser = getLoginUser(token);
        redisCacheUtil.evict(tokenKey(token));
        if (loginUser != null) {
            redisCacheUtil.evict(userIdTokenKey(loginUser.getUserId()));
        }
        log.info("Token已删除: userId={}", loginUser != null ? loginUser.getUserId() : "unknown");
    }

    // ===================== 内部工具 =====================

    private static String tokenKey(String uuid) {
        return TOKEN_PREFIX + uuid;
    }

    private static String userIdTokenKey(Long userId) {
        return USERID_PREFIX + userId;
    }
}
