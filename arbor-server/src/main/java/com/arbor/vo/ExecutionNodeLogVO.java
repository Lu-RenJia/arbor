package com.arbor.vo;

import com.arbor.entity.ExecutionNodeLog;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 节点执行日志 VO
 *
 * @author gwl
 */
@Data
@Builder
public class ExecutionNodeLogVO {

    private Long id;
    /** 关联的执行日志ID */
    private Long executionLogId;
    /** 节点ID */
    private Long nodeId;
    /** 节点编码 */
    private String nodeCode;
    /** 节点名称 */
    private String nodeName;
    /** 节点类型 */
    private String nodeType;
    /** 执行顺序 */
    private Integer sortOrder;
    /** 状态: SUCCESS/FAIL/STOPPED */
    private String status;
    /** 开始时间 */
    private LocalDateTime startTime;
    /** 结束时间 */
    private LocalDateTime endTime;
    /** 耗时(毫秒) */
    private Long duration;
    /** 入参 */
    private String inputParams;
    /** 输出结果 */
    private String outputResult;
    /** 错误信息 */
    private String errorMessage;
    /** 创建时间 */
    private LocalDateTime createTime;

    public static ExecutionNodeLogVO fromEntity(ExecutionNodeLog entity) {
        return ExecutionNodeLogVO.builder()
                .id(entity.getId())
                .executionLogId(entity.getExecutionLogId())
                .nodeId(entity.getNodeId())
                .nodeCode(entity.getNodeCode())
                .nodeName(entity.getNodeName())
                .nodeType(entity.getNodeType())
                .sortOrder(entity.getSortOrder())
                .status(entity.getStatus())
                .startTime(entity.getStartTime())
                .endTime(entity.getEndTime())
                .duration(entity.getDuration())
                .inputParams(entity.getInputParams())
                .outputResult(entity.getOutputResult())
                .errorMessage(entity.getErrorMessage())
                .createTime(entity.getCreateTime())
                .build();
    }
}
