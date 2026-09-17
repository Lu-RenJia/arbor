package com.arbor.common;

import java.util.function.Supplier;

/**
 * ThreadLocal 上下文快照 — 跨线程传递租户/环境上下文
 * <p>
 * 背景：TenantContext / EnvironmentContext 基于 ThreadLocal，异步任务（如脚本超时控制的
 * CompletableFuture 线程）拿不到调用线程的上下文。用法：提交异步任务前 {@link #capture()}，
 * 任务体内通过 {@link #wrap(Supplier)} 自动"恢复→执行→清理"，防止线程池复用导致上下文串台。
 * </p>
 *
 * @author gwl
 */
public final class ContextSnapshot {

    private final Long userId;
    private final boolean admin;
    private final boolean skipTenant;
    private final Long envId;
    private final String envCode;

    private ContextSnapshot(Long userId, boolean admin, boolean skipTenant, Long envId, String envCode) {
        this.userId = userId;
        this.admin = admin;
        this.skipTenant = skipTenant;
        this.envId = envId;
        this.envCode = envCode;
    }

    /** 在当前线程捕获上下文快照（提交异步任务前调用） */
    public static ContextSnapshot capture() {
        return new ContextSnapshot(
                TenantContext.getUserId(), TenantContext.isAdmin(), TenantContext.isSkipTenant(),
                EnvironmentContext.getEnvId(), EnvironmentContext.getEnvCode());
    }

    /** 将快照恢复到当前线程（覆盖式写入） */
    public void apply() {
        TenantContext.set(userId, admin);
        TenantContext.skipTenant(skipTenant);
        if (envId != null) {
            EnvironmentContext.set(envId, envCode);
        }
    }

    /** 清理当前线程的全部上下文（异步任务结束时调用，防止线程池复用串台） */
    public static void clearAll() {
        TenantContext.clear();
        EnvironmentContext.clear();
    }

    /** 包装 Runnable：执行前恢复快照，执行后清理 */
    public Runnable wrap(Runnable task) {
        return () -> {
            apply();
            try {
                task.run();
            } finally {
                clearAll();
            }
        };
    }

    /** 包装 Supplier：执行前恢复快照，执行后清理 */
    public <T> Supplier<T> wrap(Supplier<T> task) {
        return () -> {
            apply();
            try {
                return task.get();
            } finally {
                clearAll();
            }
        };
    }
}
