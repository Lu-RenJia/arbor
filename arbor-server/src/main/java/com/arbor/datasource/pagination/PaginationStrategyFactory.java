package com.arbor.datasource.pagination;

/**
 * 分页策略工厂 — 根据数据库类型返回对应分页策略
 *
 * @author gwl
 */
public final class PaginationStrategyFactory {

    private PaginationStrategyFactory() {}

    /**
     * 根据数据库类型获取分页策略
     *
     * @param dbType 数据库类型
     * @return 分页策略实现
     * @throws IllegalArgumentException 不支持的数据库类型
     */
    public static PaginationStrategy getStrategy(String dbType) {
        return DbType.from(dbType).getStrategy();
    }
}
