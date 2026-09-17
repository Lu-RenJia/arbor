package com.arbor.vo;

import com.arbor.entity.RedisScript;
import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * Redis 配置 VO
 *
 * @author gwl
 */
@Data
@Builder
public class RedisScriptVO {
    private Long id;
    /** 名称 */
    private String name;
    /** 所属流程ID（NULL=全局资源，非空=内联资源） */
    private Long flowId;
    /** 关联的 Redis 数据源ID */
    private Long datasourceId;
    /** Redis 数据源名称（回显用，由 Service 批量填充；查不到表示已被删除） */
    private String datasourceName;
    /** 关联的业务前缀ID（redis_key_prefix.id，必填） */
    private Long prefixId;
    /** 业务前缀值（回显用，由 Service 批量填充） */
    private String prefixValue;
    /** 命令: GET/SET/DEL/EXPIRE/TTL/EXISTS/GET&SET */
    private String command;
    /** Redis Key（不含任何系统前缀，支持 ${}） */
    private String redisKey;
    /**
     * 完整 key 预览 = {@code {prefixValue}:{redisKey}}，与执行期落地的 key 同规则。
     * <p>保留 {@code ${}} 原样（列表页拿不到运行时变量值），供溯源与排障对照。</p>
     */
    private String fullKey;
    /** Redis Value */
    private String redisValue;
    /** 过期时间 */
    private Integer ttl;
    /** TTL单位: SECONDS/MINUTES/HOURS/DAYS */
    private String ttlUnit;
    /** 备注 */
    private String remark;
    /** 创建时间 */
    private LocalDateTime createTime;
    /** 更新时间 */
    private LocalDateTime updateTime;

    public static RedisScriptVO fromEntity(RedisScript e) {
        return builder().id(e.getId()).name(e.getName()).flowId(e.getFlowId())
                .datasourceId(e.getDatasourceId()).prefixId(e.getPrefixId())
                .command(e.getCommand()).redisKey(e.getRedisKey()).redisValue(e.getRedisValue())
                .ttl(e.getTtl()).ttlUnit(e.getTtlUnit()).remark(e.getRemark())
                .createTime(e.getCreateTime()).updateTime(e.getUpdateTime()).build();
    }
}
