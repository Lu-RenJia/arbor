package com.arbor.datasource.pagination;

import com.arbor.datasource.SqlTypeDetector;

/**
 * SQL 物理分页策略接口
 *
 * @author gwl
 */
public interface PaginationStrategy {

    /**
     * 将原始SQL包装为分页查询SQL
     *
     * @param sql      原始SQL
     * @param pageNum  页码，从1开始
     * @param pageSize 每页大小
     * @return 分页SQL
     */
    String wrapSql(String sql, int pageNum, int pageSize);

    /**
     * 将原始SQL包装为 COUNT 查询SQL
     *
     * @param sql 原始SQL
     * @return COUNT SQL
     */
    String countSql(String sql);

    /**
     * 去除SQL末尾分号
     */
    default String trimSemicolon(String sql) {
        String s = sql.trim();
        while (s.endsWith(";")) {
            s = s.substring(0, s.length() - 1).trim();
        }
        return s;
    }

    /**
     * 去除末尾分号，并在首尾各补一个换行，用于包装前隔离原始SQL
     * <p>补换行是为了隔离以行注释（{@code --} 或 {@code #}）结尾的SQL。分页片段是字符串直接
     * 拼接的，若原SQL末尾是行注释，拼上去的 {@code LIMIT} 或 {@code ) 别名} 会被注释吞掉，
     * 轻则分页静默失效，重则括号不闭合直接报语法错误。</p>
     * <p>必须在包装侧补，不能指望用户自己写结尾换行：{@link #trimSemicolon} 开头的
     * {@code trim()} 会把它剥掉。</p>
     *
     * @param sql 原始SQL
     * @return 已去分号、首尾各带一个换行的SQL
     */
    default String isolate(String sql) {
        return "\n" + trimSemicolon(sql) + "\n";
    }

    /**
     * 判断 SQL 是否为查询语句
     * 非查询的 DML 不应进行分页包装或 COUNT 包装
     *
     * @param sql 原始 SQL
     * @return true 表示是 SELECT 类查询
     */
    default boolean isQuery(String sql) {
        return SqlTypeDetector.isQuery(sql);
    }
}
