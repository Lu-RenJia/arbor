package com.arbor.datasource.pagination;

/**
 * 达梦（DM）分页策略：使用 OFFSET-FETCH（达梦8+兼容）
 *
 * @author gwl
 */
public class DamengPaginationStrategy implements PaginationStrategy {

    @Override
    public String wrapSql(String sql, int pageNum, int pageSize) {
        int offset = (pageNum - 1) * pageSize;
        return "SELECT * FROM (" + isolate(sql) + ") OFFSET " + offset + " ROWS FETCH NEXT " + pageSize + " ROWS ONLY";
    }

    @Override
    public String countSql(String sql) {
        return "SELECT COUNT(*) FROM (" + isolate(sql) + ") _dm_count_t";
    }
}
