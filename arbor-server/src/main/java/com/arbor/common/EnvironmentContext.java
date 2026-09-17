package com.arbor.common;

/**
 * 环境上下文（ThreadLocal），存储当前执行链路中的环境ID和编码
 * <p>
 * 在 {@link com.arbor.service.ApplicationService#execute} 入口设置，
 * finally 块中必须调用 {@link #clear()} 清理。
 * </p>
 *
 * @author gwl
 */
public final class EnvironmentContext {

    private static final ThreadLocal<Long> ENV_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> ENV_CODE = new ThreadLocal<>();

    private EnvironmentContext() {}

    /** 设置当前执行链路的环境信息 */
    public static void set(Long envId, String envCode) {
        ENV_ID.set(envId);
        ENV_CODE.set(envCode);
    }

    /** 清理 ThreadLocal（执行结束时必须调用） */
    public static void clear() {
        ENV_ID.remove();
        ENV_CODE.remove();
    }

    /** 当前环境ID，null 表示未设置 */
    public static Long getEnvId() {
        return ENV_ID.get();
    }

    /** 当前环境编码，null 表示未设置 */
    public static String getEnvCode() {
        return ENV_CODE.get();
    }

    /** 获取当前环境ID，未设置时返回 null（调用方应查询用户默认环境） */
    public static Long getEnvIdOrDefault() {
        return ENV_ID.get();
    }
}
