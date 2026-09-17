package com.arbor.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Redis 业务前缀表单
 *
 * @author gwl
 */
@Data
public class RedisKeyPrefixDTO {
    /** 所属 Redis 数据源ID */
    @NotNull(message = "请选择所属 Redis 数据源") private Long datasourceId;
    /** 业务前缀（静态，不支持 ${}，不可含空白字符与首尾冒号） */
    @NotBlank(message = "业务前缀不能为空") private String prefix;
    /** 前缀名称 */
    @NotBlank(message = "前缀名称不能为空") private String name;
    /** 备注 */
    private String remark;
}
