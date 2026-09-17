package com.arbor.groovy;

import com.arbor.common.BusinessException;
import com.arbor.common.ErrorCode;
import com.arbor.groovy.log.ScriptLogCollector;
import com.arbor.groovy.log.ScriptLogContext;
import com.arbor.groovy.log.ScriptLogger;
import groovy.lang.Binding;
import groovy.lang.GroovyClassLoader;
import groovy.lang.Script;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Groovy 脚本执行引擎
 * <p>
 * 编译结果缓存：同脚本内容只编译一次，后续执行直接复用 Class，每次 newInstance 保证线程安全。
 * 用户脚本定义 `def execute(params) { ... }` 入口方法，通过 `params.get('xxx')` 获取参数。
 * </p>
 *
 * @author gwl
 */
@Slf4j
@Component
public class GroovyScriptEngine {

    /** 编译结果缓存，key = scriptId:md5(source) */
    private final Map<String, Class<Script>> classCache = new ConcurrentHashMap<>();

    /** 流程级 class 缓存：key = flowId:className:sha256(source)，流程内所有节点共享 */
    private final Map<String, Class<?>> flowClassCache = new ConcurrentHashMap<>();

    /** 全局工具类缓存：key = tool:className:md5(source)，所有脚本共享 */
    private final Map<String, Class<?>> toolClassCache = new ConcurrentHashMap<>();

    /** GroovyClassLoader，定期重建可防止 Metaspace 泄漏；使用沙箱编译配置 */
    private GroovyClassLoader classLoader = new GroovyClassLoader(
            GroovyScriptEngine.class.getClassLoader(), GroovySandbox.secureConfig());

    /** 脚本入口方法：用户定义 execute()，引擎自动追加一行调用 */
    private static final String ENTRY_LINE = "\nexecute()\n";

    /**
     * 执行 Groovy 脚本（编译缓存 + 运行时传参）
     *
     * @param scriptId     脚本标识
     * @param scriptSource 脚本源码（不含入口调用行）
     * @param variables    注入 Binding 的变量（key→value），由调用方从变量池/全局参数解析
     * @return execute 方法的返回值
     */
    @SuppressWarnings("unchecked")
    public Object execute(String scriptId, String scriptSource,
                          Map<String, Object> variables) {
        return execute(scriptId, scriptSource, variables, null);
    }

    /**
     * 执行 Groovy 脚本 — 附带流程ID以注入流程共享 class
     */
    @SuppressWarnings("unchecked")
    public Object execute(String scriptId, String scriptSource,
                          Map<String, Object> variables, Long flowId) {
        // 沙箱防线一：源码预扫描（拦截 .execute() 命令执行扩展等 AST 无法识别的危险写法）
        GroovySandbox.checkSource(scriptSource);
        String cacheKey = scriptId + ":" + DigestUtils.md5DigestAsHex(
                scriptSource.getBytes(StandardCharsets.UTF_8));
        Class<Script> scriptClass;
        try {
            // 沙箱防线二：编译期 SecureAST 检查，违规脚本在此抛 SecurityException（包在编译错误里）
            scriptClass = classCache.computeIfAbsent(cacheKey, k -> {
                log.info("编译Groovy脚本: scriptId={}, 长度={}", scriptId, scriptSource.length());
                return classLoader.parseClass(scriptSource + ENTRY_LINE);
            });
        } catch (Exception ce) {
            String sandboxReason = extractSandboxViolation(ce);
            if (sandboxReason != null) {
                log.warn("Groovy脚本被沙箱拦截: scriptId={}, reason={}", scriptId, sandboxReason);
                throw new BusinessException(ErrorCode.GROOVY_SANDBOX_VIOLATION, sandboxReason);
            }
            throw ce;
        }

        try {
            Script instance = scriptClass.getDeclaredConstructor().newInstance();
            Binding binding = new Binding();
            if (variables != null) {
                variables.forEach(binding::setVariable);
            }
            binding.setVariable("log", resolveScriptLogger());
            binding.setVariable("hutool", cn.hutool.core.util.StrUtil.class);
            binding.setVariable("dateUtil", com.arbor.groovy.util.DateUtil.class);
            binding.setVariable("jsonUtil", com.arbor.groovy.util.JsonUtil.class);
            binding.setVariable("dataUtil", com.arbor.groovy.util.DataTransformUtil.class);
            binding.setVariable("excelUtil", com.arbor.util.ExcelUtil.class);
            binding.setVariable("xmlUtil", new com.arbor.groovy.util.GroovyXmlUtil());
            // 平台内置 HTTP 工具（沙箱禁止脚本直连网络，HTTP 请求统一走 http.get/postJson/postForm/send）
            binding.setVariable("http", com.arbor.groovy.tool.HttpTool.class);
            binding.setVariable("FileType", java.util.Map.of("TXT", "TXT", "EXCEL", "EXCEL", "XML", "XML"));
            // 注入全局工具类（所有脚本可用）
            injectToolClasses(binding);
            // 注入流程共享 class
            if (flowId != null) {
                injectFlowClasses(flowId, binding);
            }
            instance.setBinding(binding);
            Object result = instance.run();
            result = convertGString(result);
            log.debug("Groovy脚本执行成功, 返回值类型: {}",
                    result != null ? result.getClass().getSimpleName() : "null");
            return result;
        } catch (Exception e) {
            log.error("Groovy脚本执行失败: scriptId={}", scriptId, e);
            throw new BusinessException(ErrorCode.GROOVY_SCRIPT_FAILED, buildErrorMessage(e));
        }
    }

    /**
     * 解析注入给脚本的 {@code log} 对象。
     * <p>
     * 处于流程执行上下文中（{@link ScriptLogContext} 已 open）→ 注入平台 {@link ScriptLogger}，
     * 脚本打的日志既进本次执行的收集器（供前端展示与落库）也转发服务端；
     * 无上下文（停止条件表达式、独立脚本测试等）→ 保持原行为注入引擎自身的 SLF4J logger，
     * 脚本写法不变、行为与改造前完全一致。
     * </p>
     */
    private Object resolveScriptLogger() {
        ScriptLogCollector collector = ScriptLogContext.current();
        if (collector == null) {
            return log;
        }
        return new ScriptLogger(collector, ScriptLogContext.loggerName(),
                ScriptLogContext.currentNodeCode(), log);
    }

    /**
     * 构建友好的错误消息：提取 Groovy 脚本行号 + 常见错误提示
     */
    private String buildErrorMessage(Exception e) {
        StringBuilder sb = new StringBuilder("脚本执行失败");

        // 1. 提取脚本行号
        int line = extractScriptLine(e);
        if (line > 0) {
            sb.append(" (第").append(line).append("行)");
        }

        // 2. 原始错误信息
        String msg = e.getMessage();
        if (msg != null) {
            sb.append(": ").append(msg);
        }

        // 3. 常见错误模式提示
        String hint = buildHint(e);
        if (!hint.isEmpty()) {
            sb.append("\n💡 ").append(hint);
        }

        return sb.toString();
    }

    /**
     * 从 Groovy 异常堆栈中提取脚本行号
     */
    private int extractScriptLine(Exception e) {
        for (StackTraceElement ste : e.getStackTrace()) {
            String fileName = ste.getFileName();
            // GroovyClassLoader 编译的脚本文件名形如 Script1.groovy
            if (fileName != null && fileName.endsWith(".groovy")) {
                int line = ste.getLineNumber();
                if (line > 0) return line;
            }
        }
        // 如果当前异常没有，查 cause
        Throwable cause = e.getCause();
        if (cause instanceof Exception) {
            return extractScriptLine((Exception) cause);
        }
        return -1;
    }

    /**
     * 根据异常类型给出针对性提示
     */
    private String buildHint(Exception e) {
        String msg = e.getMessage();
        if (msg == null) msg = "";
        Throwable cause = e.getCause();
        String causeMsg = cause != null ? String.valueOf(cause.getMessage()) : "";

        // 空指针相关的几种 Groovy 报错模式 → 很可能是引用了未开启的节点
        if (msg.contains("getAt() on null object")
                || (msg.contains("Cannot get property") && msg.contains("on null object"))
                || msg.contains("NullPointerException")
                || causeMsg.contains("NullPointerException")) {
            return "可能访问了 null 对象，请检查：\n"
                    + "  1) 变量池中是否配置了该变量？\n"
                    + "  2) 变量名拼写是否正确？\n"
                    + "  3) 前置节点是否执行成功（失败节点的数据为 null）？";
        }

        // MissingPropertyException → 变量未定义
        if (msg.contains("MissingPropertyException") || msg.contains("No such property")) {
            return "使用了未定义的变量，请检查：\n"
                    + "  1) 是否在「变量池维护」中配置了该变量？\n"
                    + "  2) 变量名拼写是否正确？";
        }

        // MissingMethodException → 方法不存在
        if (msg.contains("MissingMethodException") || msg.contains("No signature of method")) {
            return "调用了不存在的方法，请检查方法名拼写和参数类型是否正确";
        }

        return "";
    }

    /**
     * 递归转换返回值中的 GString 为 String，避免 Jackson 序列化出内部结构
     */
    @SuppressWarnings("unchecked")
    private static Object convertGString(Object obj) {
        if (obj instanceof groovy.lang.GString) {
            return obj.toString();
        }
        if (obj instanceof Map) {
            Map<String, Object> map = new java.util.LinkedHashMap<>();
            ((Map<String, Object>) obj).forEach((k, v) -> map.put(k, convertGString(v)));
            return map;
        }
        if (obj instanceof List) {
            return ((List<Object>) obj).stream().map(GroovyScriptEngine::convertGString).toList();
        }
        return obj;
    }

    /**
     * 驱逐指定脚本的编译缓存（脚本更新/删除时调用）
     *
     * @param scriptId 脚本标识
     */
    public void evictCache(String scriptId) {
        String prefix = scriptId + ":";
        classCache.keySet().removeIf(k -> k.startsWith(prefix));
        log.debug("Groovy编译缓存已驱逐: scriptId={}", scriptId);
    }

    /**
     * 注册流程内共享的用户自定义 class，按 className + 源码 SHA-256 缓存。
     * 同一流程的所有节点共享已编译的 class。
     *
     * @param flowId    流程ID
     * @param className 类名
     * @param source    完整类源码（Groovy/Java）
     * @return 编译后的 Class 对象
     */
    public Class<?> defineClass(Long flowId, String className, String source) {
        GroovySandbox.checkSource(source);
        String sha256 = DigestUtils.md5DigestAsHex(source.getBytes(StandardCharsets.UTF_8));
        String key = flowId + ":" + className + ":" + sha256;
        try {
            return flowClassCache.computeIfAbsent(key, k -> {
                log.info("编译用户自定义类: flowId={}, className={}", flowId, className);
                return classLoader.parseClass(source);
            });
        } catch (Exception ce) {
            String sandboxReason = extractSandboxViolation(ce);
            if (sandboxReason != null) {
                log.warn("用户自定义类被沙箱拦截: flowId={}, className={}, reason={}", flowId, className, sandboxReason);
                throw new BusinessException(ErrorCode.GROOVY_SANDBOX_VIOLATION, sandboxReason);
            }
            throw ce;
        }
    }

    /**
     * 清除指定流程的所有 class 缓存
     */
    public void evictFlowClasses(Long flowId) {
        String prefix = flowId + ":";
        flowClassCache.keySet().removeIf(k -> k.startsWith(prefix));
        log.debug("流程class缓存已清除: flowId={}", flowId);
    }

    /**
     * 将流程内已注册的 class 注入 Binding（供脚本直接 new 实例）
     */
    private void injectFlowClasses(Long flowId, Binding binding) {
        String prefix = flowId + ":";
        flowClassCache.forEach((key, clazz) -> {
            if (key.startsWith(prefix)) {
                String className = clazz.getSimpleName();
                binding.setVariable(className, clazz);
            }
        });
    }

    // ====== 全局工具类管理 ======

    /**
     * 编译并缓存全局工具类（启动预编译 / 保存时调用）
     *
     * @param className 类名（全局唯一）
     * @param source    完整类源码
     * @return 编译后的 Class 对象
     */
    public Class<?> defineToolClass(String className, String source) {
        GroovySandbox.checkSource(source);
        String md5 = DigestUtils.md5DigestAsHex(source.getBytes(StandardCharsets.UTF_8));
        String key = "tool:" + className + ":" + md5;
        try {
            return toolClassCache.computeIfAbsent(key, k -> {
                log.info("编译全局工具类: className={}", className);
                return classLoader.parseClass(source);
            });
        } catch (Exception ce) {
            String sandboxReason = extractSandboxViolation(ce);
            if (sandboxReason != null) {
                log.warn("全局工具类被沙箱拦截: className={}, reason={}", className, sandboxReason);
                throw new BusinessException(ErrorCode.GROOVY_SANDBOX_VIOLATION, sandboxReason);
            }
            throw ce;
        }
    }

    /**
     * 驱逐指定工具类的编译缓存（更新/删除时调用）
     * <p>
     * 必须连带清空脚本与流程类的编译缓存：工具类经本 classLoader 编译后，脚本编译期
     * 就能解析到它并把 Class 引用固化进脚本字节码，而脚本缓存 key 为 scriptId:md5(脚本源码)，
     * 只含脚本源码指纹、不含工具类指纹。若只清工具类缓存，源码未变的脚本会继续命中旧 Class，
     * 从而执行工具类的旧方法体（回归测试见 ToolClassHotReloadTest）。
     * </p>
     * <p>
     * 工具类是全局共享资源，无法低成本判定哪些脚本引用了它，故全量驱逐，由下次执行按需重新编译。
     * 工具类的增删改是管理员低频操作，重编译开销可接受。
     * </p>
     *
     * @param className 类名
     */
    public void evictToolClass(String className) {
        String prefix = "tool:" + className + ":";
        toolClassCache.keySet().removeIf(k -> k.startsWith(prefix));
        int scriptClasses = classCache.size();
        int flowClasses = flowClassCache.size();
        classCache.clear();
        flowClassCache.clear();
        log.info("工具类编译缓存已驱逐: className={}, 连带作废脚本编译缓存={}条, 流程类缓存={}条",
                className, scriptClasses, flowClasses);
    }

    /**
     * 将全局工具类注入 Binding（供脚本直接 new 实例或调用静态方法）
     */
    private void injectToolClasses(Binding binding) {
        toolClassCache.forEach((key, clazz) -> {
            String className = clazz.getSimpleName();
            binding.setVariable(className, clazz);
        });
    }

    /** SecureASTCustomizer 违规消息的英文特征片段（Groovy 4.x 文案） */
    private static final String[] SANDBOX_MSG_MARKERS = {
            "Method calls not allowed on",
            "Indirect method calls not allowed",
            "Importing [",
            "Instantiating [",
            "Expressions of type",
            "Catching [",
            "General error during semantic analysis"
    };

    /**
     * 从编译异常链中提取沙箱违规原因。
     * SecureASTCustomizer 抛出的 SecurityException 会被 Groovy 包装进
     * MultipleCompilationErrorsException（消息形如 "Method calls not allowed on [java.lang.System]"
     * 或 "General error during semantic analysis: ..."），按特征文本提取；
     * 非沙箱错误（语法错等）返回 null。
     */
    private static String extractSandboxViolation(Throwable t) {
        Throwable cur = t;
        int depth = 0;
        while (cur != null && depth < 10) {
            if (cur instanceof SecurityException) {
                return cur.getMessage();
            }
            if (cur instanceof org.codehaus.groovy.control.MultipleCompilationErrorsException mce) {
                String msg = mce.getMessage();
                if (msg != null) {
                    for (String marker : SANDBOX_MSG_MARKERS) {
                        if (msg.contains(marker)) {
                            // 取包含特征的首行，作为可读的违规原因返回
                            for (String line : msg.split("\\R")) {
                                if (line.contains(marker)) {
                                    return line.strip();
                                }
                            }
                            return msg.strip();
                        }
                    }
                }
            }
            cur = cur.getCause();
            depth++;
        }
        return null;
    }

    /**
     * 重建 GroovyClassLoader，清理所有编译缓存
     * 用于防止 Metaspace 持续增长
     */
    public void rebuildClassLoader() {
        GroovyClassLoader old = this.classLoader;
        this.classLoader = new GroovyClassLoader(
                GroovyScriptEngine.class.getClassLoader(), GroovySandbox.secureConfig());
        this.classCache.clear();
        this.flowClassCache.clear();
        this.toolClassCache.clear();
        try {
            old.close();
        } catch (Exception ignored) {
            // ignore
        }
        log.info("GroovyClassLoader已重建，编译缓存已清空");
    }
}
