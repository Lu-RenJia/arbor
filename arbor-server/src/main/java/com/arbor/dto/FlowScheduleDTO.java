package com.arbor.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 流程调度配置 DTO
 *
 * @author gwl
 */
@Data
public class FlowScheduleDTO {

    /** Cron表达式（Spring 6位：秒 分 时 日 月 周） */
    @NotBlank(message = "Cron表达式不能为空")
    private String cronExpr;

    /** 执行次数上限，null=不限 */
    private Integer maxExecCount;
}
