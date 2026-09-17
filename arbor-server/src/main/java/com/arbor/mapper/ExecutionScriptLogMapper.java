package com.arbor.mapper;

import com.arbor.entity.ExecutionScriptLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 脚本执行日志数据访问层（MyBatis XML）
 * <p>
 * 本表在多租户白名单中，插件不会自动补 user_id，故 insert 显式写入该列。
 * </p>
 *
 * @author gwl
 */
@Mapper
public interface ExecutionScriptLogMapper {

    /** 查某次执行的全部脚本日志，按执行内顺序返回 */
    List<ExecutionScriptLog> selectByExecutionLogId(Long executionLogId);

    /** 批量插入（调用侧按 500 条分片，避免单条 SQL 过长） */
    int insertBatch(@Param("list") List<ExecutionScriptLog> list);
}
