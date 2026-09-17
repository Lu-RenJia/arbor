package com.arbor.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 节点重试支撑：从节点 config 读取重试策略并包装执行器调用
 * <p>
 * config 字段（与停止条件字段同级，属于引擎级配置，执行前会从传给执行器的 config 中剥离）：
 * <ul>
 *   <li>{@code retryCount}     重试次数（0-10，默认 0 不重试）</li>
 *   <li>{@code retryIntervalMs} 重试间隔毫秒（0-60000，默认 2000）</li>
 * </ul>
 * 执行结果失败（NodeResult.success=false）或抛异常都会触发重试；
 * 重试耗尽后返回最后一次失败结果 / 抛出最后一次异常，由 FlowEngine 统一按节点失败处理。
 * </p>
 *
 * @author gwl
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RetrySupport {

    /** config 字段 - 重试次数 */
    public static final String FIELD_RETRY_COUNT = "retryCount";
    /** config 字段 - 重试间隔（毫秒） */
    public static final String FIELD_RETRY_INTERVAL_MS = "retryIntervalMs";
    /** 重试次数上限（防止配置失误导致长时间阻塞） */
    public static final int MAX_RETRY_COUNT = 10;
    /** 重试间隔上限（毫秒） */
    public static final long MAX_RETRY_INTERVAL_MS = 60_000L;
    /** 默认重试间隔（毫秒） */
    public static final long DEFAULT_RETRY_INTERVAL_MS = 2000L;

    private final ObjectMapper objectMapper;

    /** 重试策略（不可变） */
    public record RetryPolicy(int retryCount, long intervalMs) {
        public static final RetryPolicy NONE = new RetryPolicy(0, 0);
    }

    /**
     * 解析节点 config 中的重试策略，非法/缺失值回退默认，超界值截断
     */
    public RetryPolicy parse(String rawConfig) {
        if (rawConfig == null || !rawConfig.contains(FIELD_RETRY_COUNT)) {
            return RetryPolicy.NONE;
        }
        try {
            Map<String, Object> config = objectMapper.readValue(rawConfig, Map.class);
            int count = toInt(config.get(FIELD_RETRY_COUNT), 0);
            if (count <= 0) {
                return RetryPolicy.NONE;
            }
            long interval = toLong(config.get(FIELD_RETRY_INTERVAL_MS), DEFAULT_RETRY_INTERVAL_MS);
            return new RetryPolicy(
                    Math.min(count, MAX_RETRY_COUNT),
                    Math.max(0, Math.min(interval, MAX_RETRY_INTERVAL_MS)));
        } catch (Exception e) {
            log.warn("解析重试配置失败，按不重试处理: {}", e.getMessage());
            return RetryPolicy.NONE;
        }
    }

    /**
     * 从节点 config 中剥离重试字段（引擎级配置不应透传给节点执行器）
     */
    public String stripRetryFields(String rawConfig) {
        if (rawConfig == null || !rawConfig.contains(FIELD_RETRY_COUNT)) {
            return rawConfig;
        }
        try {
            Map<String, Object> config = objectMapper.readValue(rawConfig, Map.class);
            config.remove(FIELD_RETRY_COUNT);
            config.remove(FIELD_RETRY_INTERVAL_MS);
            return objectMapper.writeValueAsString(config);
        } catch (Exception e) {
            log.warn("剥离重试字段失败，config 原样返回: {}", e.getMessage());
            return rawConfig;
        }
    }

    /**
     * 按策略包装执行器调用：失败/异常重试，成功立即返回。
     * 重试耗尽时：最后一次是异常则重新抛出，否则返回最后一次失败结果。
     *
     * @param executor  节点执行器
     * @param context   节点执行上下文
     * @param rawConfig 节点原始 config（含重试字段）
     * @return 成功的结果，或重试耗尽后的最后一次失败结果
     */
    public NodeResult executeWithRetry(NodeExecutor executor, NodeExecutionContext context, String rawConfig) {
        RetryPolicy policy = parse(rawConfig);
        NodeResult lastResult = null;
        Exception lastException = null;
        for (int attempt = 0; attempt <= policy.retryCount(); attempt++) {
            if (attempt > 0) {
                log.warn("节点重试: code={}, 第{}/{}次, 间隔{}ms",
                        context.getNodeCode(), attempt, policy.retryCount(), policy.intervalMs());
                if (policy.intervalMs() > 0) {
                    try {
                        Thread.sleep(policy.intervalMs());
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
            try {
                lastException = null;
                lastResult = executor.execute(context);
                if (lastResult.isSuccess()) {
                    return lastResult;
                }
                log.warn("节点执行失败{}: code={}, error={}",
                        policy.retryCount() > attempt ? "（将重试）" : "",
                        context.getNodeCode(), lastResult.getErrorMessage());
            } catch (Exception e) {
                lastException = e;
                log.warn("节点执行异常{}: code={}, {}",
                        policy.retryCount() > attempt ? "（将重试）" : "",
                        context.getNodeCode(), e.getMessage());
            }
        }
        if (lastException != null) {
            // 重抛由 FlowEngine 统一记录失败日志
            throw new NodeRetryExhaustedException(lastException);
        }
        return lastResult;
    }

    private static int toInt(Object v, int def) {
        if (v instanceof Number n) return n.intValue();
        if (v instanceof String s) {
            try { return Integer.parseInt(s.trim()); } catch (NumberFormatException ignored) { }
        }
        return def;
    }

    private static long toLong(Object v, long def) {
        if (v instanceof Number n) return n.longValue();
        if (v instanceof String s) {
            try { return Long.parseLong(s.trim()); } catch (NumberFormatException ignored) { }
        }
        return def;
    }

    /** 重试耗尽且最后一次是异常时抛出（携带原始异常） */
    public static class NodeRetryExhaustedException extends RuntimeException {
        public NodeRetryExhaustedException(Exception cause) {
            super(cause.getMessage(), cause);
        }
    }
}
