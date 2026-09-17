package com.arbor.engine.executor;

import com.arbor.engine.FileOutputHandler;
import com.arbor.engine.NodeExecutionContext;
import com.arbor.engine.NodeExecutor;
import com.arbor.engine.NodeResult;
import com.arbor.service.ApiConfigService;
import com.arbor.service.FlowVariableService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 引用接口节点执行器
 * 引用已配置的接口调用
 *
 * @author gwl
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RefApiNodeExecutor implements NodeExecutor {

    /** 节点类型标识 */
    public static final String NODE_TYPE = "REF_API";

    private final ApiConfigService apiConfigService;
    private final FlowVariableService flowVariableService;
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
            Long apiConfigId = toLong(configMap.get("apiConfigId"));

            if (apiConfigId == null) {
                return NodeResult.fail("引用接口配置不完整：缺少 apiConfigId");
            }

            log.info("REF_API 执行: apiConfigId={}", apiConfigId);
            // 解析流程变量池，将变量注入接口参数解析（支持 ${varName} 引用变量池）
            Map<String, String> flowVars = resolveFlowVars(context);
            Map<String, Object> result = apiConfigService.executeForFlow(apiConfigId, flowVars);
            boolean success = (boolean) result.getOrDefault("success", false);
            if (success) {
                // 只返回接口响应 body，不携带 statusCode/responseTime 等程序逻辑字段
                Object body = result.get("body");
                // 二进制文件响应：落库为输出文件，节点 data 返回文件标记（不携带字节）
                if (body instanceof byte[] bytes) {
                    var info = fileOutputHandler.saveBinary(bytes,
                            (String) result.get("fileName"), (String) result.get("contentType"),
                            context.getFlowId());
                    if (info == null) return NodeResult.fail("接口返回二进制文件但保存失败");
                    return NodeResult.ok(FileOutputHandler.fileMarker(info));
                }
                if (body instanceof String bodyStr && !bodyStr.isBlank()) {
                    try { body = objectMapper.readValue(bodyStr, Object.class); } catch (Exception ignored) {}
                }
                return NodeResult.ok(body);
            } else {
                Object body = result.get("body");
                return NodeResult.fail("接口调用失败: " + (body instanceof byte[] ? "[二进制响应]" : body));
            }

        } catch (Exception e) {
            log.error("REF_API 执行失败", e);
            return NodeResult.fail("接口调用失败: " + e.getMessage());
        }
    }

    private Long toLong(Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value instanceof String) {
            return Long.parseLong((String) value);
        }
        return null;
    }

    /** 解析流程变量池（宽松模式），转为 String Map 供接口 ${} 解析使用 */
    private Map<String, String> resolveFlowVars(NodeExecutionContext context) {
        try {
            Map<String, Object> vars = flowVariableService.resolveVariables(
                    context.getFlowId(), context.getPreviousResults(), true);
            Map<String, String> strVars = new HashMap<>();
            vars.forEach((k, v) -> {
                if (v == null) return;
                if (v instanceof String s) {
                    strVars.put(k, s);
                } else if (v instanceof Number || v instanceof Boolean) {
                    strVars.put(k, v.toString());
                } else {
                    // 复杂对象（Map/List）序列化为 JSON，保证下游可解析
                    try { strVars.put(k, objectMapper.writeValueAsString(v)); }
                    catch (Exception e) { strVars.put(k, String.valueOf(v)); }
                }
            });
            return strVars;
        } catch (Exception e) {
            log.warn("REF_API 解析变量池失败（忽略，仅用全局参数）: {}", e.getMessage());
            return Map.of();
        }
    }
}
