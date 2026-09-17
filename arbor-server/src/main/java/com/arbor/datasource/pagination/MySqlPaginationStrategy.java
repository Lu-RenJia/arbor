package com.arbor.datasource.pagination;

/**
 * MySQL 分页策略：LIMIT offset, size
 *
 * @author gwl
 */
public class MySqlPaginationStrategy implements PaginationStrategy {

    @Override
    public String wrapSql(String sql, int pageNum, int pageSize) {
        int offset = (pageNum - 1) * pageSize;
        return isolate(sql) + "LIMIT " + offset + ", " + pageSize;
    }

    @Override
    public String countSql(String sql) {
        // 移除尾部分号 + 子查询 COUNT（换行隔离，防止以行注释结尾的 SQL 吞掉右括号与别名）
        return "SELECT COUNT(*) FROM (" + isolate(sql) + ") _dm_count_t";
    }
}
