package com.arbor.groovy.tool;

import com.arbor.common.BusinessException;
import com.arbor.common.ErrorCode;
import com.arbor.datasource.DynamicDataSourceManager;
import com.arbor.datasource.ParameterizedSql;
import com.arbor.entity.Datasource;
import lombok.extern.slf4j.Slf4j;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Groovy 脚本中执行 SQL 的工具类
 * <p>
 * 通过变量池配置的数据源自动获取连接，提供 query（查询）和 execute（增删改）方法。
 * 查询结果受 {@code arbor.sql.max-rows} 硬性限制，防止大结果集撑爆内存。
 * </p>
 * <p>
 * <b>安全约定：凡是要把外部输入（流程入参 {@code _input}、上游节点结果、HTTP 请求参数等）
 * 带进 SQL，一律用带 params 的重载，以 ? 占位后由驱动绑定，不要自己拼字符串。</b>
 * 拼接会让调用方能闭合引号改写 SQL 结构，参数绑定则始终把输入当作纯数据。
 * </p>
 * <p>使用示例：</p>
 * <pre>
 * // 固定条件、不含外部输入
 * def users = myDb.query("SELECT * FROM user WHERE age &gt; 18")
 *
 * // 带外部输入：? 占位 + 参数列表（Groovy 的 [] 就是 List）
 * def rows  = myDb.query("SELECT * FROM user WHERE name LIKE ?", ["%" + name + "%"])
 * def n     = myDb.execute("UPDATE user SET status = ? WHERE id = ?", [1, id])
 *
 * // 反例：不要这样写，name 里的引号会改写 SQL 结构
 * // myDb.query("SELECT * FROM user WHERE name = '" + name + "'")
 * </pre>
 *
 * @author gwl
 */
@Slf4j
public class SqlTool {

    private final Long datasourceId;
    private final String dbType;
    private final DynamicDataSourceManager dsManager;
    private final Datasource datasource;
    private final int maxRows;

    public SqlTool(Datasource datasource, DynamicDataSourceManager dsManager, int maxRows) {
        this.datasource = datasource;
        this.datasourceId = datasource.getId();
        this.dbType = datasource.getDbType();
        this.dsManager = dsManager;
        this.maxRows = maxRows;
    }

    /**
     * 执行查询 SQL，返回结果列表（每行为 Map，key=列名 value=值）
     * <p>结果集受 maxRows 硬限制，超出的行会被截断。
     * 仅适用于不含外部输入的固定 SQL；需要带入外部输入请用 {@link #query(String, List)}。</p>
     *
     * @param sql SELECT 语句
     * @return List&lt;Map&lt;String, Object&gt;&gt; 查询结果，最多 maxRows 条
     */
    public List<Map<String, Object>> query(String sql) {
        return query(sql, Collections.emptyList());
    }

    /**
     * 执行参数化查询：SQL 中的 ? 由 params 按序绑定
     * <p>参数个数与 ? 个数不一致时抛出可读错误，而不是驱动含义模糊的
     * "Parameter index out of range"。字面量里的 ? 不计入标记数。</p>
     *
     * @param sql    含 ? 占位符的 SELECT 语句
     * @param params 按 ? 顺序排列的参数值，元素可为 null
     * @return 查询结果，最多 maxRows 条
     */
    public List<Map<String, Object>> query(String sql, List<?> params) {
        ParameterizedSql psql = new ParameterizedSql(sql, params);
        try (Connection conn = dsManager.getConnection(datasource);
             PreparedStatement ps = conn.prepareStatement(psql.getSql())) {
            ps.setMaxRows(maxRows);
            psql.bindTo(ps);
            try (ResultSet rs = ps.executeQuery()) {
                ResultSetMetaData meta = rs.getMetaData();
                int cols = meta.getColumnCount();
                List<Map<String, Object>> result = new ArrayList<>();
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int i = 1; i <= cols; i++) {
                        row.put(meta.getColumnLabel(i), rs.getObject(i));
                    }
                    result.add(row);
                }
                if (result.size() >= maxRows) {
                    log.warn("SqlTool.query 结果已达上限: dsId={}, dbType={}, rows={}, sql前100={}",
                            datasourceId, dbType, result.size(), truncate(sql, 100));
                }
                return result;
            }
        } catch (BusinessException e) {
            // 参数校验类错误保留原错误码，不要被统一包装成 GROOVY_SQL_FAILED
            throw e;
        } catch (Exception e) {
            log.error("SqlTool.query 失败: dsId={}, dbType={}, sql={}", datasourceId, dbType,
                    truncate(sql, 200), e);
            throw new BusinessException(ErrorCode.GROOVY_SQL_FAILED, e.getMessage());
        }
    }

    /**
     * 执行增删改 SQL，返回影响行数
     * <p>仅适用于不含外部输入的固定 SQL；需要带入外部输入请用 {@link #execute(String, List)}。</p>
     *
     * @param sql INSERT / UPDATE / DELETE / DDL 语句
     * @return 影响行数（DDL 返回 0）
     */
    public int execute(String sql) {
        return execute(sql, Collections.emptyList());
    }

    /**
     * 执行参数化增删改：SQL 中的 ? 由 params 按序绑定
     *
     * @param sql    含 ? 占位符的 INSERT / UPDATE / DELETE 语句
     * @param params 按 ? 顺序排列的参数值，元素可为 null
     * @return 影响行数
     */
    public int execute(String sql, List<?> params) {
        ParameterizedSql psql = new ParameterizedSql(sql, params);
        try (Connection conn = dsManager.getConnection(datasource);
             PreparedStatement ps = conn.prepareStatement(psql.getSql())) {
            psql.bindTo(ps);
            int rows = ps.executeUpdate();
            log.debug("SqlTool.execute: dsId={}, dbType={}, rows={}", datasourceId, dbType, rows);
            return rows;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("SqlTool.execute 失败: dsId={}, dbType={}, sql={}", datasourceId, dbType,
                    truncate(sql, 200), e);
            throw new BusinessException(ErrorCode.GROOVY_SQL_FAILED, e.getMessage());
        }
    }

    /** 日志里截断 SQL，避免长语句刷屏 */
    private static String truncate(String sql, int max) {
        if (sql == null) {
            return "";
        }
        return sql.length() > max ? sql.substring(0, max) + "..." : sql;
    }
}
