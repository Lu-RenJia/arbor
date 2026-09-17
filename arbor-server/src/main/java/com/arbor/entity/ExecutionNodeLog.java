package com.arbor.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 节点执行日志实体
 *
 * @author gwl
 */
@Data
public class ExecutionNodeLog {

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
    /** 执行顺序（1-based） */
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
    /** 输出结果(JSON序列化) */
    private String outputResult;
    /** 错误信息 */
    private String errorMessage;
    /** 创建时间 */
    private LocalDateTime createTime;
}
