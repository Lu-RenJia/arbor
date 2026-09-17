package com.arbor.mapper;

import com.arbor.entity.FlowSchedule;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * 流程定时调度 Mapper（白名单表，无 user_id 列，不参与租户过滤）
 *
 * @author gwl
 */
@Mapper
public interface FlowScheduleMapper {

    FlowSchedule selectByFlowId(@Param("flowId") Long flowId);

    List<FlowSchedule> selectAllEnabled();

    /** 按流程ID批量查询（流程列表展示 cron 用） */
    List<FlowSchedule> selectByFlowIds(@Param("flowIds") Set<Long> flowIds);

    int insert(FlowSchedule entity);

    int updateByFlowId(FlowSchedule entity);

    int updateEnabled(@Param("flowId") Long flowId, @Param("enabled") Integer enabled,
                      @Param("nextExecTime") LocalDateTime nextExecTime);

    int incrExecCount(@Param("flowId") Long flowId, @Param("lastExecTime") LocalDateTime lastExecTime,
                      @Param("nextExecTime") LocalDateTime nextExecTime);

    /** 次数用尽后停用 */
    int disableByFlowId(@Param("flowId") Long flowId);

    int deleteByFlowId(@Param("flowId") Long flowId);
}
