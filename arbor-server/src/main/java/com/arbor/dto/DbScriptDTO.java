package com.arbor.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 数据库脚本 DTO
 *
 * @author gwl
 */
@Data
public class DbScriptDTO {

    /** 脚本名称 */
    @NotBlank(message = "脚本名称不能为空")
    private String name;

    /** 关联的数据源ID */
    private Long datasourceId;

    /** SQL内容 */
    @NotBlank(message = "SQL内容不能为空")
    private String sqlContent;

    /** 返回条数限制（1-200） */
    @Min(value = 1, message = "返回条数不能小于1")
    @Max(value = 200, message = "返回条数不能大于200")
    private Integer returnLimit = 10;

    /** 自定义超时秒数（1-180），不设则用系统默认 */
    @Min(value = 1, message = "超时不能小于1秒")
    @Max(value = 180, message = "超时不能大于180秒")
    private Integer queryTimeout;

    /** 备注 */
    private String remark;

    /** 所属流程ID（内联模式时传入，全局资源不传） */
    private Long flowId;
}
