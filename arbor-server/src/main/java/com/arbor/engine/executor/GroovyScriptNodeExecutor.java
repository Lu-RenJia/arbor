package com.arbor.engine.executor;

import com.arbor.engine.NodeExecutionContext;
import com.arbor.engine.NodeExecutor;
import com.arbor.engine.NodeResult;
import com.arbor.groovy.GroovyScriptEngine;
import com.arbor.service.FlowVariableService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Groovy代码节点执行器
 *
 * @author gwl
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GroovyScriptNodeExecutor implements NodeExecutor {

    public static final String NODE_TYPE = "GROOVY_SCRIPT";

    private final GroovyScriptEngine groovyScriptEngine;
    private final FlowVariableService flowVariableService;
    private final ObjectMapper objectMapper;

    @Override
    public String getNodeType() {
        return NODE_TYPE;
    }

    @Override
    public NodeResult execute(NodeExecutionContext context) {
        try {
            Map<String, Object> configMap = objectMapper.readValue(context.getConfig(), Map.class);
            String script = (String) configMap.get("script");
            if (script == null || script.isBlank()) {
                return NodeResult.fail("脚本内容为空");
            }

            // 从变量池解析变量（全局参数 + 已执行节点数据）
            Map<String, Object> variables = flowVariableService.resolveVariables(
                    context.getFlowId(), context.getPreviousResults());

            String scriptId = "node:" + context.getNodeId();
            log.info("GROOVY_SCRIPT 执行: nodeCode={}, 变量数={}",
                    context.getNodeCode(), variables.size());
            Object result = groovyScriptEngine.execute(scriptId, script, variables, context.getFlowId());
            return NodeResult.ok(result);

        } catch (Exception e) {
            log.error("GROOVY_SCRIPT 执行失败", e);
            return NodeResult.fail("脚本执行失败: " + e.getMessage());
        }
    }
}
