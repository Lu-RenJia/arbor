package com.arbor.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 流程定时调度配置
 *
 * @author gwl
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class FlowSchedule extends BaseEntity {

    /** 关联流程ID */
    private Long flowId;

    /** Cron表达式（Spring 6位：秒 分 时 日 月 周） */
    private String cronExpr;

    /** 是否启用：0-停用 1-启用 */
    private Integer enabled;

    /** 执行次数上限，NULL=不限 */
    private Integer maxExecCount;

    /** 已执行次数 */
    private Integer execCount;

    /** 上次执行时间 */
    private LocalDateTime lastExecTime;

    /** 下次执行时间 */
    private LocalDateTime nextExecTime;
}
