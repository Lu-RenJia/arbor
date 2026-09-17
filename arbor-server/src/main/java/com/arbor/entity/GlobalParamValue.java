package com.arbor.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 全局参数-环境值实体
 *
 * @author gwl
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class GlobalParamValue extends BaseEntity {

    /** 参数ID (global_param.id) */
    private Long paramId;

    /** 环境ID (environment.id) */
    private Long envId;

    /** 参数值 */
    private String paramValue;
}
