package com.arbor.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 通用实体基类
 *
 * @author gwl
 */
@Data
public abstract class BaseEntity {

    /** 主键ID */
    private Long id;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 最后修改时间 */
    private LocalDateTime updateTime;
}
