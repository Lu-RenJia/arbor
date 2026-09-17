package com.arbor.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class RedisScript extends BaseEntity {
    /** 名称 */
    private String name;
    /** 所属流程ID（NULL=全局资源，非空=内联资源） */
    private Long flowId;
    /** 关联的 Redis 数据源ID */
    private Long datasourceId;
    /** 关联业务前缀ID（redis_key_prefix.id）—— 最终 key = {prefix}:{redisKey}，必填 */
    private Long prefixId;
    /** 命令: GET/SET/DEL/EXPIRE/TTL/EXISTS/GET&SET */
    private String command;
    /** Redis Key（支持 ${} 表达式；不含任何系统前缀，业务前缀由 prefixId 指定） */
    private String redisKey;
    /** Redis Value（支持 ${} 表达式，GET/GET&SET 无需配置） */
    private String redisValue;
    /** 过期时间 */
    private Integer ttl;
    /** TTL单位: SECONDS/MINUTES/HOURS/DAYS */
    private String ttlUnit;
    /** 备注 */
    private String remark;
    /** 全局唯一标识（导入导出匹配用，应用层创建时生成UUID） */
    private String uid;
}
