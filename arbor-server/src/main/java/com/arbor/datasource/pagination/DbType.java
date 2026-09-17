package com.arbor.datasource.pagination;

/**
 * 数据库类型枚举 — 统一管理支持的数据库及分页策略
 *
 * @author gwl
 */
public enum DbType {

    MYSQL(new MySqlPaginationStrategy()),
    ORACLE(new OraclePaginationStrategy()),
    DAMENG(new DamengPaginationStrategy()),
    POSTGRESQL(new PostgreSqlPaginationStrategy());

    private final PaginationStrategy strategy;

    DbType(PaginationStrategy strategy) {
        this.strategy = strategy;
    }

    public PaginationStrategy getStrategy() {
        return strategy;
    }

    /**
     * 根据字符串类型名查找枚举
     *
     * @param typeName 类型名（不区分大小写）
     * @return DbType 枚举
     * @throws IllegalArgumentException 不支持的数据库类型
     */
    public static DbType from(String typeName) {
        for (DbType t : values()) {
            if (t.name().equalsIgnoreCase(typeName.trim())) {
                return t;
            }
        }
        throw new IllegalArgumentException("不支持的数据库类型: " + typeName + "，支持的类型: " + supportedTypes());
    }

    /** 返回所有支持的类型名，用于提示 */
    public static String supportedTypes() {
        StringBuilder sb = new StringBuilder();
        for (DbType t : values()) {
            if (!sb.isEmpty()) sb.append(", ");
            sb.append(t.name());
        }
        return sb.toString();
    }
}
