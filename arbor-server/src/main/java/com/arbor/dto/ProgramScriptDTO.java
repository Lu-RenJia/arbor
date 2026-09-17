package com.arbor.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 程序脚本 DTO
 *
 * @author gwl
 */
@Data
public class ProgramScriptDTO {

    /** 脚本名称 */
    @NotBlank(message = "脚本名称不能为空")
    private String name;

    /** 脚本内容（Groovy） */
    @NotBlank(message = "脚本内容不能为空")
    private String scriptContent;

    /** 自定义超时秒数（1-300），不设则用系统默认 */
    @Min(value = 1, message = "超时不能小于1秒")
    @Max(value = 300, message = "超时不能大于300秒")
    private Integer queryTimeout;

    /** 备注 */
    private String remark;

    /** 所属流程ID（内联模式时传入，全局资源不传） */
    private Long flowId;
}
