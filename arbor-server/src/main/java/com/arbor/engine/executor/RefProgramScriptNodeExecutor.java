package com.arbor.engine.executor;

import com.arbor.engine.NodeExecutionContext;
import com.arbor.engine.NodeExecutor;
import com.arbor.engine.NodeResult;
import com.arbor.service.ProgramScriptService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 引用程序脚本节点执行器
 *
 * @author gwl
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RefProgramScriptNodeExecutor implements NodeExecutor {

    public static final String NODE_TYPE = "REF_PROGRAM_SCRIPT";

    private final ProgramScriptService programScriptService;
    private final ObjectMapper objectMapper;

    @Override
    public String getNodeType() {
        return NODE_TYPE;
    }

    @Override
    public NodeResult execute(NodeExecutionContext context) {
        try {
            Map<String, Object> configMap = objectMapper.readValue(context.getConfig(), Map.class);
            Object scriptIdObj = configMap.get("programScriptId");
            String scriptId = scriptIdObj != null ? scriptIdObj.toString() : null;
            if (scriptId == null) {
                return NodeResult.fail("引用程序脚本配置不完整：缺少 programScriptId");
            }

            log.info("REF_PROGRAM_SCRIPT 执行: scriptId={}, flowId={}", scriptId, context.getFlowId());
            Object result = programScriptService.runScript(
                    scriptId, context.getFlowId(), context.getPreviousResults());
            return NodeResult.ok(result);

        } catch (Exception e) {
            log.error("REF_PROGRAM_SCRIPT 执行失败", e);
            return NodeResult.fail("程序脚本执行失败: " + e.getMessage());
        }
    }
}
