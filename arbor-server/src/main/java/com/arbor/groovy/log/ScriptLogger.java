package com.arbor.groovy.log;

import org.slf4j.Logger;
import org.slf4j.helpers.FormattingTuple;
import org.slf4j.helpers.MessageFormatter;

/**
 * 注入给 Groovy 脚本的 {@code log} 对象 —— 替换掉原先直接注入的引擎自身 SLF4J logger。
 * <p>
 * 脚本写法完全不变（{@code log.info("处理{}条", n)}、{@code log.warn}、{@code log.error(msg, e)}），
 * 无需 import、无需声明。每条日志<b>双写</b>：
 * ① 进本次执行的 {@link ScriptLogCollector}（供前端展示与落库）；
 * ② 转发到服务端 {@link Logger}（服务端日志不丢，且带完整堆栈）。
 * </p>
 * <p>
 * <b>健壮性优先</b>：格式化、收集、转发三段各自兜异常，任何一段失败都只降级不抛出 ——
 * 打日志绝不能把脚本执行搞挂。
 * </p>
 * <p>
 * 占位符沿用 SLF4J 语义（{@code {}}），由 {@link MessageFormatter#arrayFormat} 处理，
 * 因此 {@code log.error("失败: {}", e)} 这种「末位参数是 Throwable」的写法也能被正确识别为异常。
 * </p>
 *
 * @author gwl
 */
public class ScriptLogger {

    /** 与 {@link ScriptLogContext} 一致的兜底 logger 名，用于判断构造时是否拿到了真实节点名 */
    private static final String FALLBACK_LOGGER = "script";

    /** 异常信息并入消息尾的分隔符 */
    private static final String THROWABLE_SEPARATOR = " | ";

    private final ScriptLogCollector collector;

    /** 构造时固化的 logger 名（= 节点名称），避免实例被脚本存下来跨节点使用时归属错乱 */
    private final String loggerName;

    /** 构造时固化的节点编码 */
    private final String nodeCode;

    /** 服务端日志转发目标，可为 null（此时只收集不转发） */
    private final Logger delegate;

    public ScriptLogger(ScriptLogCollector collector, String loggerName, String nodeCode, Logger delegate) {
        this.collector = collector;
        this.loggerName = loggerName;
        this.nodeCode = nodeCode;
        this.delegate = delegate;
    }

    // ===== DEBUG =====

    public void debug(String msg) {
        write("DEBUG", msg, null, null);
    }

    public void debug(String format, Object arg) {
        write("DEBUG", format, new Object[]{arg}, null);
    }

    public void debug(String format, Object arg1, Object arg2) {
        write("DEBUG", format, new Object[]{arg1, arg2}, null);
    }

    public void debug(String format, Object... arguments) {
        write("DEBUG", format, arguments, null);
    }

    public void debug(String msg, Throwable t) {
        write("DEBUG", msg, null, t);
    }

    // ===== INFO =====

    public void info(String msg) {
        write("INFO", msg, null, null);
    }

    public void info(String format, Object arg) {
        write("INFO", format, new Object[]{arg}, null);
    }

    public void info(String format, Object arg1, Object arg2) {
        write("INFO", format, new Object[]{arg1, arg2}, null);
    }

    public void info(String format, Object... arguments) {
        write("INFO", format, arguments, null);
    }

    public void info(String msg, Throwable t) {
        write("INFO", msg, null, t);
    }

    // ===== WARN =====

    public void warn(String msg) {
        write("WARN", msg, null, null);
    }

    public void warn(String format, Object arg) {
        write("WARN", format, new Object[]{arg}, null);
    }

    public void warn(String format, Object arg1, Object arg2) {
        write("WARN", format, new Object[]{arg1, arg2}, null);
    }

    public void warn(String format, Object... arguments) {
        write("WARN", format, arguments, null);
    }

    public void warn(String msg, Throwable t) {
        write("WARN", msg, null, t);
    }

    // ===== ERROR =====

    public void error(String msg) {
        write("ERROR", msg, null, null);
    }

    public void error(String format, Object arg) {
        write("ERROR", format, new Object[]{arg}, null);
    }

    public void error(String format, Object arg1, Object arg2) {
        write("ERROR", format, new Object[]{arg1, arg2}, null);
    }

    public void error(String format, Object... arguments) {
        write("ERROR", format, arguments, null);
    }

    public void error(String msg, Throwable t) {
        write("ERROR", msg, null, t);
    }

    // ===== 级别探测：脚本里可用 if (log.isDebugEnabled()) 包一层，一律放行 =====

    public boolean isDebugEnabled() {
        return true;
    }

    public boolean isInfoEnabled() {
        return true;
    }

    public boolean isWarnEnabled() {
        return true;
    }

    public boolean isErrorEnabled() {
        return true;
    }

    public boolean isTraceEnabled() {
        return true;
    }

    /**
     * 统一写入入口：格式化 → 收集 → 转发，三段各自兜异常。
     *
     * @param level     级别
     * @param format    消息模板（可能含 {} 占位符）
     * @param args      占位符参数，无参数时为 null
     * @param explicitT 显式传入的异常（{@code error(msg, t)} 形态），可为 null
     */
    private void write(String level, String format, Object[] args, Throwable explicitT) {
        String text;
        Throwable t = explicitT;
        try {
            if (args == null || args.length == 0) {
                text = String.valueOf(format);
            } else {
                FormattingTuple tuple = MessageFormatter.arrayFormat(String.valueOf(format), args);
                text = tuple.getMessage();
                // SLF4J 语义：占位符用不完的末位 Throwable 视为异常
                if (t == null) {
                    t = tuple.getThrowable();
                }
            }
        } catch (Throwable formatError) {
            // 格式化本身出错也要把原始模板落下来，不能让日志凭空消失
            text = String.valueOf(format) + " [日志格式化失败: " + formatError + "]";
        }

        // ① 收集（供展示/落库）：异常信息并入消息尾，因为条目结构里没有 throwable 字段
        try {
            if (collector != null) {
                collector.add(level, resolveLoggerName(), resolveNodeCode(), resolveNodeName(),
                        t != null ? text + THROWABLE_SEPARATOR + t : text);
            }
        } catch (Throwable ignored) {
            // 收集失败不影响脚本执行，也不影响服务端日志
        }

        // ② 转发服务端（原始消息 + 独立 throwable，服务端日志能打出完整堆栈且不重复异常文本）
        try {
            forward(level, text, t);
        } catch (Throwable ignored) {
            // 同上
        }
    }

    private void forward(String level, String msg, Throwable t) {
        if (delegate == null) {
            return;
        }
        switch (level) {
            case "ERROR" -> {
                if (t != null) delegate.error(msg, t); else delegate.error(msg);
            }
            case "WARN" -> {
                if (t != null) delegate.warn(msg, t); else delegate.warn(msg);
            }
            case "DEBUG" -> {
                if (t != null) delegate.debug(msg, t); else delegate.debug(msg);
            }
            default -> {
                if (t != null) delegate.info(msg, t); else delegate.info(msg);
            }
        }
    }

    /** logger 名：构造时固化的优先，缺失时兜底取当前上下文 */
    private String resolveLoggerName() {
        return loggerName != null && !loggerName.isBlank() ? loggerName : ScriptLogContext.loggerName();
    }

    /** 节点编码：构造时固化的优先，缺失时兜底取当前上下文 */
    private String resolveNodeCode() {
        return nodeCode != null && !nodeCode.isBlank() ? nodeCode : ScriptLogContext.currentNodeCode();
    }

    /** 节点名称：优先当前上下文，无上下文时退回构造时的 logger 名（兜底名 "script" 则留空） */
    private String resolveNodeName() {
        String ctxName = ScriptLogContext.currentNodeName();
        if (ctxName != null && !ctxName.isBlank()) {
            return ctxName;
        }
        return FALLBACK_LOGGER.equals(loggerName) ? null : loggerName;
    }
}
