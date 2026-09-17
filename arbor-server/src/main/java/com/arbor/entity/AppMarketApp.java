package com.arbor.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AppMarketApp {
    private Long id;
    /** 广场ID */
    private Long marketId;
    /** 应用ID */
    private Long appId;
    /** 所有者指定的环境ID */
    private Long envId;
    /** 共享操作人ID */
    private Long sharedBy;
    /** 共享时间 */
    private LocalDateTime createTime;
}
