package com.arbor.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 流程执行日志实体
 *
 * @author gwl
 */
@Data
public class ExecutionLog {

    private Long id;
    /** 流程ID */
    private Long flowId;
    /** 流程名称（冗余，方便直接展示） */
    private String flowName;
    /** 用户ID（租户） */
    private Long userId;
    /** 应用ID（通过应用执行时有值） */
    private Long appId;
    /** 关联的流程快照ID */
    private Long flowSnapshotId;
    /** 执行时的入参 */
    private String inputParams;
    /** 执行方式: MANUAL/AUTO */
    private String executeType;
    /** 日志来源: FLOW_TEST/APP/FLOW_MANUAL/FLOW_AUTO */
    private String source;
    /** 所属人(流程/应用创建者) */
    private Long ownerId;
    /** 执行人(实际触发者) */
    private Long executorId;
    /** 状态: SUCCESS/FAIL/STOPPED/CANCELLED */
    private String status;
    /** 开始时间 */
    private LocalDateTime startTime;
    /** 结束时间 */
    private LocalDateTime endTime;
    /** 耗时(毫秒) */
    private Long duration;
    /** 错误信息（STOPPED 时存停止原因） */
    private String errorMessage;
    /** 停止类型：NORMAL=正常停止 / ABNORMAL=异常停止（仅 status=STOPPED 时有值） */
    private String stopType;
    /** 创建时间 */
    private LocalDateTime createTime;

    // ===== 查询时 JOIN 填充，非表字段 =====
    /** 所属人姓名 */
    private transient String ownerName;
    /** 执行人姓名 */
    private transient String executorName;
}
