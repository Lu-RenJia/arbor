package com.arbor.mapper;

import com.arbor.entity.FlowSnapshot;
import com.arbor.entity.FlowNodeSnapshot;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SnapshotMapper {

    // Flow snapshot
    FlowSnapshot selectLatestByFlowId(Long flowId);
    int insertSnapshot(FlowSnapshot entity);
    int deleteByFlowId(Long flowId);

    // Node snapshot
    int insertNodeSnapshots(@Param("list") List<FlowNodeSnapshot> list);
    List<FlowNodeSnapshot> selectBySnapshotId(Long snapshotId);

    // Check if flow has execution log references
    int countExecutionLogByFlowId(Long flowId);
    // Check if node has execution node log references
    int countNodeLogByNodeId(Long nodeId);
}
