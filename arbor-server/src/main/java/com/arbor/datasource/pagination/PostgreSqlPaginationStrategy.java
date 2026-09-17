package com.arbor.datasource.pagination;

/**
 * PostgreSQL 分页策略：LIMIT size OFFSET offset
 *
 * @author gwl
 */
public class PostgreSqlPaginationStrategy implements PaginationStrategy {

    @Override
    public String wrapSql(String sql, int pageNum, int pageSize) {
        int offset = (pageNum - 1) * pageSize;
        return isolate(sql) + "LIMIT " + pageSize + " OFFSET " + offset;
    }

    @Override
    public String countSql(String sql) {
        return "SELECT COUNT(*) FROM (" + isolate(sql) + ") _dm_count_t";
    }
}
