package com.arbor.common;

/**
 * 租户上下文（ThreadLocal），存储当前请求的用户ID和是否管理员
 *
 * @author gwl
 */
public final class TenantContext {

    private static final ThreadLocal<Long> USER_ID = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> IS_ADMIN = new ThreadLocal<>();
    /** 跳过租户过滤标记（用于执行广场共享应用，避免引用的 db_script 等资源被过滤） */
    private static final ThreadLocal<Boolean> SKIP_TENANT = new ThreadLocal<>();

    private TenantContext() {}

    /** 设置当前请求的租户信息 */
    public static void set(Long userId, boolean admin) {
        USER_ID.set(userId);
        IS_ADMIN.set(admin);
    }

    /** 清理 ThreadLocal（请求结束时必须调用） */
    public static void clear() {
        USER_ID.remove();
        IS_ADMIN.remove();
        SKIP_TENANT.remove();
    }

    /** 临时跳过租户过滤（用于执行共享应用） */
    public static void skipTenant(boolean skip) {
        SKIP_TENANT.set(skip);
    }

    /** 是否跳过租户过滤 */
    public static boolean isSkipTenant() {
        return Boolean.TRUE.equals(SKIP_TENANT.get());
    }

    /** 当前用户ID，null 表示未登录或管理员 */
    public static Long getUserId() {
        return USER_ID.get();
    }

    /** 当前用户是否为管理员 */
    public static boolean isAdmin() {
        return Boolean.TRUE.equals(IS_ADMIN.get());
    }
}
