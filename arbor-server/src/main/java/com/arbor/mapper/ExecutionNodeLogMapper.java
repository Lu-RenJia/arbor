package com.arbor.mapper;

import com.arbor.entity.ExecutionNodeLog;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 节点执行日志数据访问层（MyBatis XML）
 *
 * @author gwl
 */
@Mapper
public interface ExecutionNodeLogMapper {

    List<ExecutionNodeLog> selectByExecutionLogId(Long executionLogId);

    int insert(ExecutionNodeLog entity);
}
