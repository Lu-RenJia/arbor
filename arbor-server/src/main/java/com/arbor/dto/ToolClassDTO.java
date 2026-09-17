package com.arbor.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 工具类 DTO（创建/更新）
 *
 * @author gwl
 */
@Data
public class ToolClassDTO {

    /** 工具类名称（中文描述） */
    @NotBlank(message = "工具类名称不能为空")
    private String name;

    /** Groovy 类源码（必须包含 class 定义） */
    @NotBlank(message = "工具类代码不能为空")
    private String scriptContent;

    /** 备注 */
    private String remark;
}
