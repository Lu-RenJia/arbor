package com.arbor.groovy.log;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 脚本日志收集器 —— 一次流程执行对应一个实例，按时间顺序收集脚本打出的日志。
 * <p>
 * 由执行链路在开始时创建并绑定到 {@link ScriptLogContext}，脚本里的 {@code log.xxx(...)}
 * 经 {@link ScriptLogger} 写入这里；执行结束后 {@link #snapshot()} 拿到扁平有序列表，
 * 直接用于返回前端与落库。
 * </p>
 * <p>
 * <b>线程安全</b>：子流程、REDIS 分支节点、重试都在同线程内写入，但收集器仍可能被
 * 跨线程兜底路径共享，故 {@code add}/{@code snapshot} 走同一监视器，保证
 * 「判上限 + 追加截断提示」这一复合操作的原子性（否则并发下会出现多条截断提示或提示丢失）。
 * </p>
 * <p>
 * <b>双上限</b>：条数达 {@code maxEntries} 后丢弃后续日志并只追加一条 WARN，防止脚本
 * 在循环里打日志把内存/响应体撑爆；单条消息超 {@code maxMessageLength} 以 {@code …} 截断。
 * </p>
 *
 * @author gwl
 */
public class ScriptLogCollector {

    /** 单次执行默认条数上限 */
    public static final int DEFAULT_MAX_ENTRIES = 2000;

    /** 单条消息默认字符上限 */
    public static final int DEFAULT_MAX_MESSAGE_LENGTH = 1000;

    /** 对外统一的时间格式（毫秒精度），前端直接显示不再转换 */
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    /** 归属信息缺失时的兜底 logger 名 */
    private static final String FALLBACK_LOGGER = "script";

    private final List<ScriptLogEntry> entries = Collections.synchronizedList(new ArrayList<>());

    /** 是否已因条数上限截断（只置位一次，保证只追加一条提示） */
    private final AtomicBoolean truncated = new AtomicBoolean(false);

    private final int maxEntries;
    private final int maxMessageLength;

    public ScriptLogCollector() {
        this(DEFAULT_MAX_ENTRIES, DEFAULT_MAX_MESSAGE_LENGTH);
    }

    public ScriptLogCollector(int maxEntries, int maxMessageLength) {
        this.maxEntries = maxEntries > 0 ? maxEntries : DEFAULT_MAX_ENTRIES;
        this.maxMessageLength = maxMessageLength > 0 ? maxMessageLength : DEFAULT_MAX_MESSAGE_LENGTH;
    }

    /**
     * 追加一条日志。
     * <p>
     * 已达条数上限时：首次置位追加一条 WARN 截断提示，之后直接丢弃（不再入列）。
     * 任何情况下都不抛异常 —— 打日志失败绝不能影响脚本执行。
     * </p>
     *
     * @param level    级别（DEBUG/INFO/WARN/ERROR，空值按 INFO）
     * @param logger   logger 名（一般为节点名称）
     * @param nodeCode 节点编码
     * @param nodeName 节点名称
     * @param message  已格式化的消息文本
     */
    public synchronized void add(String level, String logger, String nodeCode,
                                 String nodeName, String message) {
        if (entries.size() >= maxEntries) {
            if (truncated.compareAndSet(false, true)) {
                entries.add(build("WARN", logger, nodeCode, nodeName,
                        "日志条数已达上限 " + maxEntries + "，后续日志已丢弃"));
            }
            return;
        }
        entries.add(build(normalizeLevel(level), logger, nodeCode, nodeName, truncate(message)));
    }

    /** 当前收集到的条目副本（供落库/返回，调用方改动不影响内部状态） */
    public synchronized List<ScriptLogEntry> snapshot() {
        return new ArrayList<>(entries);
    }

    /** 是否发生过条数截断 */
    public boolean isTruncated() {
        return truncated.get();
    }

    /** 当前条目数（含截断提示那一行） */
    public int size() {
        return entries.size();
    }

    private ScriptLogEntry build(String level, String logger, String nodeCode,
                                 String nodeName, String message) {
        // 必须截断到毫秒：now() 带微秒，而 time 字符串是截断取值、MySQL DATETIME(3) 落库是
        // 四舍五入，两者会差 1ms —— 同一条日志在设计器与执行历史里显示的时间就不一致了
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS);
        return ScriptLogEntry.builder()
                .level(level)
                .time(now.format(TIME_FORMATTER))
                .logger(logger != null && !logger.isBlank() ? logger : FALLBACK_LOGGER)
                .nodeCode(nodeCode)
                .nodeName(nodeName)
                .message(message)
                .timestamp(now)
                .build();
    }

    private static String normalizeLevel(String level) {
        return level == null || level.isBlank() ? "INFO" : level.toUpperCase(Locale.ROOT);
    }

    private String truncate(String message) {
        if (message == null) {
            return "null";
        }
        if (message.length() <= maxMessageLength) {
            return message;
        }
        return message.substring(0, maxMessageLength) + "…";
    }
}
