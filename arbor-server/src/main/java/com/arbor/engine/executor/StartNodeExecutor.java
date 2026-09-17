package com.arbor.engine.executor;

import com.arbor.engine.NodeExecutionContext;
import com.arbor.engine.NodeExecutor;
import com.arbor.engine.NodeResult;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 开始节点执行器 — 解析 flow_param 入参供后续节点引用
 *
 * @author gwl
 */
@Slf4j
@Component
public class StartNodeExecutor implements NodeExecutor {

    /** 节点类型标识 */
    public static final String NODE_TYPE = "START";
    public static final String FLOW_PARAM_KEY = "_input";
    private static final String OLD_PARAM_KEY = "flow_param";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String getNodeType() {
        return NODE_TYPE;
    }

    @Override
    public NodeResult execute(NodeExecutionContext context) {
        log.info("START 节点: flowId={}, nodeCode={}", context.getFlowId(), context.getNodeCode());
        try {
            Map<String, Object> configMap = objectMapper.readValue(context.getConfig(), Map.class);
            // _input（新）优先，回退 flow_param（旧）
            Object inputParams = configMap.get(FLOW_PARAM_KEY);
            if (inputParams == null) inputParams = configMap.get(OLD_PARAM_KEY);
            if (inputParams != null) {
                log.info("START 节点入参: {}", objectMapper.writeValueAsString(inputParams));
            }
            // 解码测试文件 base64 → byte[]
            String testFileName = configMap.get("_testFileName") != null ? String.valueOf(configMap.get("_testFileName")) : null;
            String testFileData = configMap.get("_testFileData") != null ? String.valueOf(configMap.get("_testFileData")) : null;
            // 统一返回包裹结构 {_input: ...}，供 FlowEngine/FlowService 按 _input 键提取。
            // 不能直接返回原始入参：当入参本身是 JSON 对象（Map）时，上游会误判为包裹结构、
            // 在其中查找 _input 键得 null，导致 _input 未注册，后续脚本报 No such property: _input。
            Map<String, Object> result = new java.util.LinkedHashMap<>();
            result.put(FLOW_PARAM_KEY, inputParams);
            if (testFileData != null) {
                String base64 = testFileData.substring(testFileData.indexOf(',') + 1);
                byte[] bytes = java.util.Base64.getDecoder().decode(base64);
                result.put("_testFileName", testFileName);
                result.put("_testFileBytes", bytes);
            }
            return NodeResult.ok(result);
        } catch (Exception e) {
            log.error("START 节点解析入参失败", e);
            return NodeResult.fail("入参解析失败: " + e.getMessage());
        }
    }
}
