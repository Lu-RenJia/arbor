package com.arbor.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
public class AppMarket extends BaseEntity {
    /** 广场名称 */
    private String name;
    /** 备注 */
    private String remark;
    /** 创建人ID */
    private Long createBy;
    /** 最后修改人ID */
    private Long updateBy;
    /** 删除标记: 0=正常, 2=已删除 */
    private String delFlag;
}
