package com.arbor.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 全局参数实体（参数定义，参数值已移至 global_param_value 表）
 *
 * @author gwl
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class GlobalParam extends BaseEntity {

    /** 参数名（同环境下唯一） */
    private String paramName;

    /** 备注 */
    private String remark;
}
