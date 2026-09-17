package com.arbor.engine.executor;

import com.arbor.datasource.DynamicDataSourceManager;
import com.arbor.datasource.ParameterizedSql;
import com.arbor.datasource.SqlTypeDetector;
import com.arbor.engine.NodeExecutionContext;
import com.arbor.engine.NodeExecutor;
import com.arbor.engine.NodeResult;
import com.arbor.engine.ParamResolver;
import com.arbor.entity.Datasource;
import com.arbor.service.DatasourceService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据库查询节点执行器
 * 从配置中取 datasourceId 和 sql，在指定数据源上执行SQL查询
 *
 * @author gwl
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DbQueryNodeExecutor implements NodeExecutor {

    /** 节点类型标识 */
    public static final String NODE_TYPE = "DB_QUERY";

    private final DatasourceService datasourceService;
    private final DynamicDataSourceManager dynamicDataSourceManager;
    private final ObjectMapper objectMapper;
    private final ParamResolver paramResolver;

    @Override
    public String getNodeType() {
        return NODE_TYPE;
    }

    @Override
    public NodeResult execute(NodeExecutionContext context) {
        try {
            // 从原始配置中提取 SQL 模板（避免使用已内联参数值的 resolved config）
            String rawConfig = context.getRawConfig() != null ? context.getRawConfig() : context.getConfig();
            Map<String, Object> rawConfigMap = objectMapper.readValue(rawConfig, Map.class);
            Long datasourceId = toLong(rawConfigMap.get("datasourceId"));
            String sqlTemplate = (String) rawConfigMap.get("sql");

            if (datasourceId == null || sqlTemplate == null) {
                return NodeResult.fail("数据库查询节点配置不完整：缺少 datasourceId 或 sql");
            }

            // 参数化解析：${xxx} → ? 占位 + 参数值列表
            ParameterizedSql psql = paramResolver.resolveParameterized(
                    sqlTemplate, context.getGlobalParams(), context.getPreviousResults());
            String sql = psql.getSql();

            Datasource ds = datasourceService.getResolvedDatasource(datasourceId);
            // INFO 只记模板与参数个数：参数值可能包含全局参数里的密钥，降为 DEBUG
            log.info("DB_QUERY 执行: datasource={}, paramCount={}, sql={}", ds.getName(), psql.paramCount(), sql);
            log.debug("DB_QUERY 参数值: {}", psql.getParams());

            // DML 分支：INSERT/UPDATE/DELETE 等
            if (SqlTypeDetector.isDml(sql)) {
                try (Connection conn = dynamicDataSourceManager.getConnection(ds);
                     PreparedStatement ps = conn.prepareStatement(sql)) {
                    psql.bindTo(ps);
                    int affected = ps.executeUpdate();
                    log.info("DB_QUERY DML结果: affectedRows={}", affected);
                    return NodeResult.ok(Map.of("affectedRows", affected));
                }
            }

            // SELECT 分支
            try (Connection conn = dynamicDataSourceManager.getConnection(ds);
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                psql.bindTo(ps);
                try (ResultSet rs = ps.executeQuery()) {
                    ResultSetMetaData meta = rs.getMetaData();
                    int columnCount = meta.getColumnCount();
                    List<Map<String, Object>> resultList = new ArrayList<>();

                    while (rs.next()) {
                        Map<String, Object> row = new HashMap<>();
                        for (int i = 1; i <= columnCount; i++) {
                            row.put(meta.getColumnLabel(i), rs.getObject(i));
                        }
                        resultList.add(row);
                    }
                    log.info("DB_QUERY 结果行数: {}", resultList.size());
                    return NodeResult.ok(resultList);
                }
            }
        } catch (Exception e) {
            log.error("DB_QUERY 执行失败", e);
            return NodeResult.fail("数据库查询失败: " + e.getMessage());
        }
    }

    /** 配置里的 datasourceId 可能是数字或字符串，统一转 Long */
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
