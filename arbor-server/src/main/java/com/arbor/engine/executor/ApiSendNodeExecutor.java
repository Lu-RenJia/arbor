package com.arbor.engine.executor;

import com.arbor.engine.FileOutputHandler;
import com.arbor.engine.NodeExecutionContext;
import com.arbor.engine.NodeExecutor;
import com.arbor.engine.NodeResult;
import com.arbor.util.HttpClientUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;

/**
 * 接口发送节点执行器
 * 从配置中取 method/url/headers/params/body，通过 HttpClientUtil 发送请求
 *
 * @author gwl
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApiSendNodeExecutor implements NodeExecutor {

    /** 节点类型标识 */
    public static final String NODE_TYPE = "API_SEND";

    private final HttpClientUtil httpClientUtil;
    private final ObjectMapper objectMapper;
    private final FileOutputHandler fileOutputHandler;

    @Override
    public String getNodeType() {
        return NODE_TYPE;
    }

    @Override
    public NodeResult execute(NodeExecutionContext context) {
        try {
            Map<String, Object> configMap = objectMapper.readValue(context.getConfig(), Map.class);
            String method = (String) configMap.get("method");
            String url = (String) configMap.get("url");
            Integer timeout = configMap.get("timeout") != null
                    ? ((Number) configMap.get("timeout")).intValue() : 30;

            if (method == null || url == null) {
                return NodeResult.fail("接口配置不完整：缺少 method 或 url");
            }

            Map<String, String> headerMap = parseToStringMap(configMap.get("headers"));
            Map<String, String> paramMap = parseToStringMap(configMap.get("params"));
            String bodyType = (String) configMap.get("bodyType");
            String bodyContent = (String) configMap.get("bodyContent");

            log.info("API_SEND 执行: {} {}", method.toUpperCase(), url);
            Map<String, Object> response = httpClientUtil.send(
                    method, url, headerMap, paramMap, bodyType, bodyContent, timeout);

            boolean success = (boolean) response.getOrDefault("success", false);
            if (success) {
                // 二进制文件响应：落库为输出文件，节点 data 返回文件标记（不携带字节）
                if (response.get("body") instanceof byte[] bytes) {
                    var info = fileOutputHandler.saveBinary(bytes,
                            (String) response.get("fileName"), (String) response.get("contentType"),
                            context.getFlowId());
                    if (info == null) return NodeResult.fail("接口返回二进制文件但保存失败");
                    return NodeResult.ok(FileOutputHandler.fileMarker(info));
                }
                return NodeResult.ok(response);
            } else {
                Object body = response.get("body");
                return NodeResult.fail("接口调用失败: " + (body instanceof byte[] ? "[二进制响应]" : body));
            }
        } catch (Exception e) {
            log.error("API_SEND 执行失败", e);
            return NodeResult.fail("接口调用失败: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> parseToStringMap(Object obj) {
        if (obj instanceof Map) {
            return (Map<String, String>) obj;
        }
        if (obj instanceof String && !((String) obj).isBlank()) {
            try {
                return objectMapper.readValue((String) obj, new TypeReference<Map<String, String>>() {});
            } catch (Exception e) {
                return Collections.emptyMap();
            }
        }
        return Collections.emptyMap();
    }
}
