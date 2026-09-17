package com.arbor.groovy.log;

/**
 * 脚本日志上下文 —— 用 ThreadLocal 持有「本次执行的收集器 + 当前节点归属」。
 * <p>
 * 这是整个方案的关键：执行链路开始时 {@link #open()}，每个节点执行前 {@link #currentNode}
 * 更新归属，结束时 {@link #close()}。{@link com.arbor.groovy.GroovyScriptEngine} 构建 Binding
 * 时从本上下文取收集器，因此引擎方法签名不用改、{@code NodeResult} 不用加字段、
 * 各种把执行结果包装成 timedResult 的点一个都不用碰；子流程 / REDIS 分支 / 重试同线程，
 * 日志天然冒泡且归属正确。
 * </p>
 * <p>
 * <b>必须成对使用</b>：调用方一律 {@code try { ... } finally { close(); }}。线程池复用下
 * 漏解绑会导致下一次执行串到上一次的收集器里（既是脏数据也是内存泄漏）。
 * </p>
 * <p>
 * {@link #open()} 带重入计数：嵌套打开时复用外层收集器而不是覆盖它，{@link #close()}
 * 只在最外层真正解绑，避免内层调用把外层的上下文提前清掉。
 * </p>
 *
 * @author gwl
 */
public final class ScriptLogContext {

    /** 归属信息缺失时的兜底 logger 名 */
    private static final String FALLBACK_LOGGER = "script";

    private static final ThreadLocal<Holder> TL = new ThreadLocal<>();

    private ScriptLogContext() {
    }

    /** 上下文持有者：收集器 + 当前节点归属 + 重入深度 */
    private static final class Holder {
        final ScriptLogCollector collector;
        String nodeCode;
        String nodeName;
        int depth = 1;

        Holder(ScriptLogCollector collector) {
            this.collector = collector;
        }
    }

    /**
     * 开启一次执行的日志采集，返回收集器（调用方持有引用，close 后仍可取 snapshot）。
     * 已开启时复用外层收集器并累加重入深度。
     */
    public static ScriptLogCollector open() {
        Holder holder = TL.get();
        if (holder != null) {
            holder.depth++;
            return holder.collector;
        }
        ScriptLogCollector collector = new ScriptLogCollector();
        TL.set(new Holder(collector));
        return collector;
    }

    /** 当前线程是否已开启采集 */
    public static boolean isOpen() {
        return TL.get() != null;
    }

    /** 当前收集器，无上下文时返回 null（调用方据此决定注入 ScriptLogger 还是原生 slf4j logger） */
    public static ScriptLogCollector current() {
        Holder holder = TL.get();
        return holder != null ? holder.collector : null;
    }

    /**
     * 更新当前节点归属。无上下文时静默忽略 —— 子流程执行器只调本方法不 open，
     * 独立测试等无采集场景也不该因此报错。
     */
    public static void currentNode(String nodeCode, String nodeName) {
        Holder holder = TL.get();
        if (holder == null) {
            return;
        }
        holder.nodeCode = nodeCode;
        holder.nodeName = nodeName;
    }

    /** 当前 logger 名（= 节点名称），缺省 "script" */
    public static String loggerName() {
        Holder holder = TL.get();
        if (holder == null || holder.nodeName == null || holder.nodeName.isBlank()) {
            return FALLBACK_LOGGER;
        }
        return holder.nodeName;
    }

    /** 当前节点编码，无上下文时返回 null */
    public static String currentNodeCode() {
        Holder holder = TL.get();
        return holder != null ? holder.nodeCode : null;
    }

    /** 当前节点名称，无上下文时返回 null */
    public static String currentNodeName() {
        Holder holder = TL.get();
        return holder != null ? holder.nodeName : null;
    }

    /**
     * 解绑当前线程的上下文，幂等。
     * <p>
     * 只解绑，不清空已收集的条目（条目由 {@link #open()} 返回的收集器引用持有，
     * 落库与返回体在那之后才消费）。嵌套打开时按深度递减，最外层才真正 remove。
     * </p>
     */
    public static void close() {
        Holder holder = TL.get();
        if (holder == null) {
            return;
        }
        if (--holder.depth > 0) {
            return;
        }
        TL.remove();
    }
}
