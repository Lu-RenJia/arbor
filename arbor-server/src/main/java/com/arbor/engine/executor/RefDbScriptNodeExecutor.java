package com.arbor.engine.executor;

import com.arbor.engine.NodeExecutionContext;
import com.arbor.engine.NodeExecutor;
import com.arbor.engine.NodeResult;
import com.arbor.service.DbScriptService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 引用数据库脚本节点执行器
 * 从脚本内置的 datasource_id 获取数据源执行
 *
 * @author gwl
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RefDbScriptNodeExecutor implements NodeExecutor {

    /** 节点类型标识 */
    public static final String NODE_TYPE = "REF_DB_SCRIPT";

    private final DbScriptService dbScriptService;
    private final ObjectMapper objectMapper;

    @Override
    public String getNodeType() {
        return NODE_TYPE;
    }

    @Override
    public NodeResult execute(NodeExecutionContext context) {
        try {
            Map<String, Object> configMap = objectMapper.readValue(context.getConfig(), Map.class);
            Long dbScriptId = toLong(configMap.get("dbScriptId"));

            if (dbScriptId == null) {
                return NodeResult.fail("引用数据库脚本配置不完整：缺少 dbScriptId");
            }

            log.info("REF_DB_SCRIPT 执行: scriptId={}", dbScriptId);
            return NodeResult.ok(dbScriptService.executeWithScriptDatasource(dbScriptId, context.getPreviousResults()));

        } catch (Exception e) {
            log.error("REF_DB_SCRIPT 执行失败", e);
            return NodeResult.fail("数据库脚本执行失败: " + e.getMessage());
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
}
