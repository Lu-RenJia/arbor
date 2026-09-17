package com.arbor.groovy.tool;

import com.arbor.common.BusinessException;
import com.arbor.common.ErrorCode;
import com.arbor.service.GlobalParamService;
import com.arbor.util.HttpClientUtil;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Groovy 脚本中的 HTTP 调用工具（平台内置，替代脚本直连网络）
 * <p>
 * 沙箱禁止脚本直接网络访问（Socket/URL/HttpURLConnection）及 .execute() 链式调用，
 * 脚本内的 HTTP 请求统一走本工具：复用接口节点的 {@link HttpClientUtil}（共享连接池、
 * 超时控制、统一日志），并支持 ${全局参数} 占位符自动解析（按当前环境取值）。
 * </p>
 * <p>
 * 普通程序脚本（Binding 注入 http）：
 * <pre>
 * def body = http.get('http://host/api', [appid: '1'], [page: 1])
 * def body2 = http.postJson(url, ['Content-Type': 'application/json'], '{"a":1}')
 * def body3 = http.postForm('${global_platformUrl}', headers, formMap)
 * </pre>
 * 全局工具类（静态方法上下文，import 后直接调用）：
 * <pre>
 * import com.arbor.groovy.tool.HttpTool
 * String result = HttpTool.get('${global_platformUrl}', headerMap, formParams)
 * </pre>
 * </p>
 *
 * @author gwl
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HttpTool {

    private final HttpClientUtil httpClientUtil;
    private final GlobalParamService globalParamService;

    /** 静态门面单例：Groovy 工具类静态方法拿不到 Spring Bean，经此委托 */
    private static volatile HttpTool instance;

    @PostConstruct
    void init() {
        instance = this;
    }

    /** ${参数名} 占位符（仅支持全局参数名，与 ParamResolver 的命名约定一致） */
    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([a-zA-Z_]\\w*)\\}");

    // ==================== 脚本友好的静态 API ====================

    /** GET 请求，返回响应体字符串 */
    public static String get(String url) {
        return send("GET", url, null, null, null, null, null);
    }

    /** GET 请求（带请求头与 URL 参数），返回响应体字符串 */
    public static String get(String url, Map<String, ?> headers, Map<String, ?> params) {
        return send("GET", url, headers, params, null, null, null);
    }

    /** POST JSON 请求，返回响应体字符串 */
    public static String postJson(String url, Map<String, ?> headers, String jsonBody) {
        return send("POST", url, headers, null, "json", jsonBody, null);
    }

    /** POST JSON 请求（paramMap 转 JSON 请求体），返回响应体字符串 */
    public static String postJson(String url, Map<String, ?> headers, Map<String, ?> paramMap) {
        return postJson(url, headers, toJson(paramMap));
    }

    /** POST 表单请求（form 转 x-www-form-urlencoded），返回响应体字符串 */
    public static String postForm(String url, Map<String, ?> headers, Map<String, ?> form) {
        return send("POST", url, headers, null, "urlencoded", toJson(form), null);
    }

    /**
     * 通用请求，返回响应体字符串；非 2xx 或网络失败抛 BusinessException
     *
     * @param method      GET/POST/PUT/DELETE 等
     * @param url         请求地址（支持 ${全局参数} 占位符）
     * @param headers     请求头（值支持占位符），可为 null
     * @param params      URL 参数（值支持占位符），可为 null
     * @param bodyType    json / urlencoded / none，可为 null
     * @param body        请求体（支持占位符），可为 null
     * @param timeoutSecs 超时秒数，null 用默认 30s
     */
    public static String send(String method, String url, Map<String, ?> headers, Map<String, ?> params,
                              String bodyType, String body, Integer timeoutSecs) {
        Map<String, Object> result = request(method, url, headers, params, bodyType, body, timeoutSecs);
        if (!Boolean.TRUE.equals(result.get("success"))) {
            throw new BusinessException(ErrorCode.GROOVY_SCRIPT_FAILED,
                    "HTTP " + method.toUpperCase() + " " + url + " 调用失败: statusCode="
                            + result.get("statusCode") + ", " + result.get("body"));
        }
        Object b = result.get("body");
        if (b instanceof byte[] bytes) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
        return b != null ? String.valueOf(b) : null;
    }

    /**
     * 通用请求，返回完整结果 Map（statusCode / responseTime / body / success），不抛异常，
     * 供脚本需要自行判断状态码的场景使用
     */
    public static Map<String, Object> request(String method, String url, Map<String, ?> headers,
                                              Map<String, ?> params, String bodyType, String body,
                                              Integer timeoutSecs) {
        HttpTool tool = instance;
        if (tool == null) {
            throw new BusinessException(ErrorCode.GROOVY_SCRIPT_FAILED, "HttpTool 未初始化（应用启动未完成）");
        }
        // headers 传空 Map 而非 null：HttpClientUtil 在 urlencoded 分支会访问 headers
        Map<String, String> h = toStringMap(headers, tool);
        return tool.httpClientUtil.send(method, tool.resolvePlaceholders(url),
                h != null ? h : new LinkedHashMap<>(), toStringMap(params, tool),
                bodyType, tool.resolvePlaceholders(body), timeoutSecs);
    }

    /** 把文本中的 ${全局参数} 占位符替换为当前环境的参数值；未定义的占位符原样保留 */
    public static String resolve(String text) {
        HttpTool tool = instance;
        return tool != null ? tool.resolvePlaceholders(text) : text;
    }

    // ==================== 内部实现 ====================

    private String resolvePlaceholders(String text) {
        if (text == null || !text.contains("${")) {
            return text;
        }
        Map<String, String> params = globalParamService.getAllParams();
        Matcher m = PLACEHOLDER.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String val = params.get(m.group(1));
            m.appendReplacement(sb, Matcher.quoteReplacement(val != null ? val : m.group(0)));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /** Map<String,?> → Map<String,String>，值同步做占位符解析 */
    private static Map<String, String> toStringMap(Map<String, ?> map, HttpTool tool) {
        if (map == null || map.isEmpty()) {
            return null;
        }
        Map<String, String> r = new LinkedHashMap<>();
        map.forEach((k, v) -> r.put(k, v == null ? null : tool.resolvePlaceholders(String.valueOf(v))));
        return r;
    }

    private static String toJson(Map<String, ?> form) {
        if (form == null || form.isEmpty()) {
            return null;
        }
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(form);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.GROOVY_SCRIPT_FAILED, "表单参数序列化失败: " + e.getMessage());
        }
    }
}
