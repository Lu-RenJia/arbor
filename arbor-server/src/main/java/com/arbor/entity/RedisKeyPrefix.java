package com.arbor.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Redis 业务前缀实体
 * <p>
 * 一个 Redis 数据源下可登记多个业务前缀，配置 Redis 脚本时必须选定其中一个，
 * 最终落地的 key = {@code {prefix}:{redisKey}}。前缀承担层级管理与溯源职责，
 * 取代原先由系统强加的 env/user/flow 前缀。
 * </p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class RedisKeyPrefix extends BaseEntity {
    /** 所属 Redis 数据源ID */
    private Long datasourceId;
    /** 业务前缀（静态字符串，不支持 ${}，内部可含冒号如 arbor:order） */
    private String prefix;
    /** 前缀名称（便于识别，如 订单业务） */
    private String name;
    /** 备注 */
    private String remark;
    /** 全局唯一标识（导入导出匹配用，应用层创建时生成UUID） */
    private String uid;
}
