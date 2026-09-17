package com.arbor.datasource.pagination;

/**
 * Oracle 分页策略：使用 OFFSET-FETCH（Oracle 12c+）
 *
 * @author gwl
 */
public class OraclePaginationStrategy implements PaginationStrategy {

    @Override
    public String wrapSql(String sql, int pageNum, int pageSize) {
        int startRow = (pageNum - 1) * pageSize + 1;
        int endRow = pageNum * pageSize;
        return "SELECT * FROM (" +
                "  SELECT t.*, ROWNUM rn FROM (" + isolate(sql) + ") t WHERE ROWNUM <= " + endRow +
                ") WHERE rn >= " + startRow;
    }

    @Override
    public String countSql(String sql) {
        return "SELECT COUNT(*) FROM (" + isolate(sql) + ") DM_COUNT_T"; // 别名改为字母开头
    }
}
