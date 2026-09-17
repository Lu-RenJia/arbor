package com.arbor.datasource;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * SQL 语句类型自动检测工具
 * <p>
 * 根据 SQL 前缀判断是查询还是 DML，用于决定使用 executeQuery 还是 executeUpdate。
 * 仅读取首关键字做分类，不修改或拼接用户 SQL。
 * </p>
 *
 * @author gwl
 */
public final class SqlTypeDetector {

    /** SELECT 类 SQL 前缀（大小写不敏感） */
    private static final Set<String> QUERY_PREFIXES = Set.of(
            "SELECT", "WITH", "SHOW", "DESCRIBE", "DESC", "EXPLAIN"
    );

    /** DML / DDL 类 SQL 前缀 */
    private static final Set<String> DML_PREFIXES = Set.of(
            "INSERT", "UPDATE", "DELETE", "REPLACE", "MERGE",
            "TRUNCATE", "CREATE", "ALTER", "DROP", "RENAME"
    );

    /** 剥离 SQL 头部注释 */
    private static final Pattern COMMENT_PATTERN =
            Pattern.compile("^\\s*(/\\*.*?\\*/|--[^\n]*|#.*\n)\\s*", Pattern.DOTALL);

    private SqlTypeDetector() {}

    /**
     * 判断 SQL 是否为查询语句
     *
     * @param sql 原始 SQL
     * @return true 表示可以使用 executeQuery 执行
     */
    public static boolean isQuery(String sql) {
        if (sql == null || sql.isBlank()) {
            return false;
        }
        String normalized = stripComments(sql).trim().toUpperCase();
        for (String prefix : QUERY_PREFIXES) {
            if (normalized.startsWith(prefix + " ") || normalized.equals(prefix)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断 SQL 是否为 DML/DDL 语句
     *
     * @param sql 原始 SQL
     * @return true 表示应使用 executeUpdate 执行
     */
    public static boolean isDml(String sql) {
        if (sql == null || sql.isBlank()) {
            return false;
        }
        String normalized = stripComments(sql).trim().toUpperCase();
        for (String prefix : DML_PREFIXES) {
            if (normalized.startsWith(prefix + " ") || normalized.equals(prefix)) {
                return true;
            }
        }
        return false;
    }

    /** 剥离 SQL 头部注释，避免注释内容干扰前缀判断 */
    private static String stripComments(String sql) {
        return COMMENT_PATTERN.matcher(sql).replaceFirst("");
    }
}
