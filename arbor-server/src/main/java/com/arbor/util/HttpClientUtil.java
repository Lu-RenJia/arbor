package com.arbor.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * HTTP 客户端工具类
 * 用于接口配置的测试调用
 *
 * @author gwl
 */
@Slf4j
@Component
public class HttpClientUtil {

    /** 默认超时（秒） */
    private static final int DEFAULT_TIMEOUT = 30;

    /**
     * 共享 HttpClient 单例 — 线程安全，内置连接池（keep-alive 复用 TCP 连接）。
     * 每次请求 new 会导致连接无法复用且每个实例占用独立 selector 线程。
     * 超时按请求粒度设置在 HttpRequest 上，不在此处。
     */
    private static final HttpClient CLIENT = HttpClient.newBuilder().build();

    /**
     * 发送 HTTP 请求
     *
     * @param method  请求方式（GET/POST/PUT/DELETE）
     * @param url     请求地址
     * @param headers 请求头 Map
     * @param params  URL 参数 Map
     * @param bodyType Body类型
     * @param bodyContent Body内容
     * @param timeout 超时（秒）
     * @return 响应结果 Map（statusCode, responseTime, body）；
     *         二进制响应时 body 为 byte[]，并额外携带 binary=true, contentType, fileName（若可解析）
     */
    public Map<String, Object> send(String method, String url, Map<String, String> headers,
                                     Map<String, String> params, String bodyType,
                                     String bodyContent, Integer timeout) {
        Map<String, Object> result = new LinkedHashMap<>();
        long startTime = System.currentTimeMillis();

        try {
            // 构建 URL（追加 query params）
            String fullUrl = buildUrl(url, params);

            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(fullUrl));

            // 设置请求头
            if (headers != null) {
                headers.forEach(requestBuilder::header);
            }

            // 设置超时
            int timeoutSecs = (timeout != null && timeout > 0) ? timeout : DEFAULT_TIMEOUT;
            requestBuilder.timeout(Duration.ofSeconds(timeoutSecs));

            // 构建 Body
            HttpRequest.BodyPublisher bodyPublisher = HttpRequest.BodyPublishers.noBody();
            if (bodyContent != null && !bodyContent.isEmpty()
                    && !"none".equalsIgnoreCase(bodyType)) {
                String actualBody = bodyContent;
                // urlencoded 类型：将 JSON 字符串转为 key=value&... 格式
                if ("urlencoded".equalsIgnoreCase(bodyType)) {
                    actualBody = jsonToUrlEncoded(bodyContent);
                    if (!headers.containsKey("Content-Type")) {
                        requestBuilder.header("Content-Type", "application/x-www-form-urlencoded");
                    }
                } else if ("json".equalsIgnoreCase(bodyType)) {
                    if (!headers.containsKey("Content-Type")) {
                        requestBuilder.header("Content-Type", "application/json");
                    }
                }
                bodyPublisher = HttpRequest.BodyPublishers.ofString(actualBody);
            }

            // 根据 method 设置
            switch (method.toUpperCase()) {
                case "GET" -> requestBuilder.GET();
                case "POST" -> requestBuilder.POST(bodyPublisher);
                case "PUT" -> requestBuilder.PUT(bodyPublisher);
                case "DELETE" -> requestBuilder.DELETE();
                default -> requestBuilder.method(method.toUpperCase(), bodyPublisher);
            }

            HttpRequest request = requestBuilder.build();
            // 统一按字节接收，再根据 Content-Type 区分文本/二进制，避免二进制响应被 UTF-8 解码成乱码
            HttpResponse<byte[]> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofByteArray());

            long responseTime = System.currentTimeMillis() - startTime;
            result.put("statusCode", response.statusCode());
            result.put("responseTime", responseTime + "ms");
            String contentType = response.headers().firstValue("Content-Type").orElse("");
            if (isTextual(contentType)) {
                result.put("body", new String(response.body(), charsetOf(contentType)));
            } else {
                result.put("body", response.body());
                result.put("binary", true);
                result.put("contentType", contentType);
                String fileName = parseFileName(response.headers().firstValue("Content-Disposition").orElse(null));
                if (fileName != null) result.put("fileName", fileName);
            }
            result.put("success", response.statusCode() >= 200 && response.statusCode() < 300);

            log.info("HTTP {} {} → {} ({}ms)", method.toUpperCase(), fullUrl,
                    response.statusCode(), responseTime);

        } catch (Exception e) {
            long responseTime = System.currentTimeMillis() - startTime;
            result.put("statusCode", 0);
            result.put("responseTime", responseTime + "ms");
            result.put("body", "请求失败: " + e.getMessage());
            result.put("success", false);
            log.error("HTTP请求失败: {} {}", method, url, e);
        }

        return result;
    }

    /** 文本类 Content-Type 判断（json/xml/text/html 等），其余视为二进制文件响应 */
    private static boolean isTextual(String contentType) {
        if (contentType == null || contentType.isBlank()) return true; // 未知按文本处理，兼容旧行为
        String ct = contentType.toLowerCase();
        return ct.startsWith("text/") || ct.contains("json") || ct.contains("xml")
                || ct.contains("urlencoded") || ct.contains("html");
    }

    /** 从 Content-Type 提取 charset，缺省 UTF-8 */
    private static java.nio.charset.Charset charsetOf(String contentType) {
        if (contentType != null) {
            for (String part : contentType.split(";")) {
                String p = part.trim();
                if (p.toLowerCase().startsWith("charset=")) {
                    try { return java.nio.charset.Charset.forName(p.substring(8).trim()); } catch (Exception ignored) {}
                }
            }
        }
        return StandardCharsets.UTF_8;
    }

    /** 从 Content-Disposition 解析文件名（支持 filename= 与 RFC 5987 的 filename*=） */
    static String parseFileName(String disposition) {
        if (disposition == null || disposition.isBlank()) return null;
        // 优先 filename*=UTF-8''xxx（URL 编码）
        java.util.regex.Matcher ext = java.util.regex.Pattern
                .compile("filename\\*\\s*=\\s*([^']*)''([^;]+)", java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(disposition);
        if (ext.find()) {
            try { return java.net.URLDecoder.decode(ext.group(2).trim(), StandardCharsets.UTF_8); } catch (Exception ignored) {}
        }
        java.util.regex.Matcher plain = java.util.regex.Pattern
                .compile("filename\\s*=\\s*\"?([^\";]+)\"?", java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(disposition);
        if (plain.find()) {
            String name = plain.group(1).trim();
            if (!name.isEmpty()) return name;
        }
        return null;
    }

    /** 拼接 URL 参数 */
    private String buildUrl(String baseUrl, Map<String, String> params) {
        if (params == null || params.isEmpty()) {
            return baseUrl;
        }
        StringBuilder sb = new StringBuilder(baseUrl);
        if (!baseUrl.contains("?")) {
            sb.append("?");
        } else {
            sb.append("&");
        }
        boolean first = true;
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (!first) sb.append("&");
            sb.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8))
              .append("=")
              .append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
            first = false;
        }
        return sb.toString();
    }

    /** 将 JSON 对象字符串转为 x-www-form-urlencoded 格式（key=value&key2=value2） */
    private String jsonToUrlEncoded(String json) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = new com.fasterxml.jackson.databind.ObjectMapper().readValue(json, Map.class);
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                if (sb.length() > 0) sb.append("&");
                sb.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8))
                  .append("=")
                  .append(URLEncoder.encode(String.valueOf(entry.getValue()), StandardCharsets.UTF_8));
            }
            return sb.toString();
        } catch (Exception e) {
            // 解析失败则原样返回（可能用户直接填写了 urlencoded 格式字符串）
            log.warn("urlencoded body 解析失败，原样发送: {}", e.getMessage());
            return json;
        }
    }
}
