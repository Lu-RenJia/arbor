package com.arbor.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 全局参数 DTO
 *
 * @author gwl
 */
@Data
public class GlobalParamDTO {

    /** 参数名（创建时自动加 global_ 前缀） */
    @NotBlank(message = "参数名不能为空")
    private String paramName;

    /** 参数值（创建时写入默认环境） */
    private String paramValue;

    /** 备注 */
    private String remark;
}
