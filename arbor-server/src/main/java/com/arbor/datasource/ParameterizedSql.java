package com.arbor.datasource;

import com.arbor.common.BusinessException;
import com.arbor.common.ErrorCode;
import lombok.Getter;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 参数化 SQL：SQL 模板（含 ? 占位符）+ 有序参数值列表
 * <p>
 * 用于替代字符串拼接 SQL，配合 PreparedStatement 参数绑定防止 SQL 注入。
 * 由 {@link SqlParameterizer} 产出，绑定动作统一收口在 {@link #bindTo}，
 * 避免各调用方各写一遍 setObject 循环。
 * </p>
 * <p>放在 datasource 包而非 engine 包：它是 JDBC 层的数据结构，
 * service 与 engine 都会用到，归属 JDBC 基础设施可避免 service → engine 的反向依赖。</p>
 *
 * @author gwl
 */
@Getter
public class ParameterizedSql {

    /** 含 ? 占位符的 SQL 模板 */
    private final String sql;

    /** 有序参数值列表，与 SQL 中 ? 的位置一一对应 */
    private final List<Object> params;

    public ParameterizedSql(String sql, List<?> params) {
        this.sql = sql;
        this.params = params != null ? new ArrayList<>(params) : Collections.emptyList();
    }

    /** 无占位符的纯 SQL（兼容无变量的场景） */
    public static ParameterizedSql of(String sql) {
        return new ParameterizedSql(sql, Collections.emptyList());
    }

    /** 待绑定参数个数 */
    public int paramCount() {
        return params.size();
    }

    /**
     * 复用同一份参数、替换 SQL 文本
     * <p>用于分页策略包装 SQL（LIMIT/ROWNUM/OFFSET 均为硬编码整数，不引入新的 ?），
     * 包装后占位符顺序与个数不变，故参数列表可原样沿用。</p>
     */
    public ParameterizedSql withSql(String newSql) {
        return new ParameterizedSql(newSql, params);
    }

    /**
     * 按序把参数绑定到 PreparedStatement
     * <p>绑定前先比对「SQL 中真正的标记个数」与「参数个数」：两者不等时驱动会抛出
     * 含义模糊的 "Parameter index out of range"，提前拦下来换成可读错误。
     * 字面量与注释里的 ? 不计入标记数（见 {@link SqlParameterizer#countMarkers}）。</p>
     * <p>null 值走 setNull：部分驱动对 setObject(i, null) 无法推断 SQL 类型。</p>
     *
     * @param ps 已用 {@link #getSql()} 准备好的语句
     * @throws SQLException 驱动层绑定错误
     */
    public void bindTo(PreparedStatement ps) throws SQLException {
        int markers = SqlParameterizer.countMarkers(sql);
        if (markers != params.size()) {
            throw new BusinessException(ErrorCode.SQL_PARAM_MARKER_MISMATCH, markers, params.size());
        }
        for (int i = 0; i < params.size(); i++) {
            Object value = params.get(i);
            if (value == null) {
                ps.setNull(i + 1, Types.NULL);
            } else {
                ps.setObject(i + 1, value);
            }
        }
    }
}
