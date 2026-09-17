package com.arbor.vo;

import com.arbor.entity.ExecutionLog;
import com.arbor.entity.ExecutionNodeLog;
import com.arbor.groovy.log.ScriptLogEntry;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 流程执行日志 VO
 *
 * @author gwl
 */
@Data
@Builder
public class ExecutionLogVO {

    private Long id;
    /** 流程ID */
    private Long flowId;
    /** 流程名称 */
    private String flowName;
    /** 执行方式: MANUAL/AUTO */
    private String executeType;
    /** 日志来源: FLOW_TEST/APP/FLOW_MANUAL/FLOW_AUTO */
    private String source;
    /** 所属人ID */
    private Long ownerId;
    /** 所属人姓名 */
    private String ownerName;
    /** 执行人ID */
    private Long executorId;
    /** 执行人姓名 */
    private String executorName;
    /** 状态: SUCCESS/FAIL/STOPPED */
    private String status;
    /** 开始时间 */
    private LocalDateTime startTime;
    /** 结束时间 */
    private LocalDateTime endTime;
    /** 耗时(毫秒) */
    private Long duration;
    /** 错误信息 */
    private String errorMessage;
    /** 创建时间 */
    private LocalDateTime createTime;

    /** 节点日志（详情才返回） */
    private List<ExecutionNodeLogVO> nodeLogs;

    /**
     * 脚本日志（详情才返回），来自 {@code execution_script_log}。
     * 结构与设计器执行结果的 {@code scriptLogs} 一致，前端日志控制台两处复用。
     */
    private List<ScriptLogEntry> scriptLogs;

    public static ExecutionLogVO fromEntity(ExecutionLog entity) {
        return ExecutionLogVO.builder()
                .id(entity.getId())
                .flowId(entity.getFlowId())
                .flowName(entity.getFlowName())
                .executeType(entity.getExecuteType())
                .source(entity.getSource())
                .ownerId(entity.getOwnerId())
                .ownerName(entity.getOwnerName())
                .executorId(entity.getExecutorId())
                .executorName(entity.getExecutorName())
                .status(entity.getStatus())
                .startTime(entity.getStartTime())
                .endTime(entity.getEndTime())
                .duration(entity.getDuration())
                .errorMessage(entity.getErrorMessage())
                .createTime(entity.getCreateTime())
                .build();
    }

    public static ExecutionLogVO fromEntityWithNodes(ExecutionLog entity, List<ExecutionNodeLog> nodeLogs) {
        ExecutionLogVO vo = fromEntity(entity);
        if (nodeLogs != null) {
            vo.setNodeLogs(nodeLogs.stream().map(ExecutionNodeLogVO::fromEntity).collect(Collectors.toList()));
        }
        return vo;
    }
}
