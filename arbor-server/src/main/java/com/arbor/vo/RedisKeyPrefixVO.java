package com.arbor.vo;

import com.arbor.entity.RedisKeyPrefix;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Redis 业务前缀视图对象
 * <p>
 * 三个计数供前缀列表展示与删除保护联动（D11）：{@code flowCount} 按 flow_id 去重，
 * {@code nodeCount} 不去重，两者语义不同不可合并成一个「使用次数」。
 * </p>
 */
@Data
@Builder
public class RedisKeyPrefixVO {
    private Long id;
    /** 所属 Redis 数据源ID */
    private Long datasourceId;
    /** 业务前缀 */
    private String prefix;
    /** 前缀名称 */
    private String name;
    /** 备注 */
    private String remark;
    /** 引用该前缀的 Redis 配置数（>0 时不允许删除） */
    private Integer scriptCount;
    /** 使用该前缀的流程数（按 flow_id 去重） */
    private Integer flowCount;
    /** 使用该前缀的节点数（不去重） */
    private Integer nodeCount;
    /** 创建时间 */
    private LocalDateTime createTime;
    /** 更新时间 */
    private LocalDateTime updateTime;

    public static RedisKeyPrefixVO fromEntity(RedisKeyPrefix e) {
        return builder().id(e.getId()).datasourceId(e.getDatasourceId()).prefix(e.getPrefix())
                .name(e.getName()).remark(e.getRemark())
                .scriptCount(0).flowCount(0).nodeCount(0)
                .createTime(e.getCreateTime()).updateTime(e.getUpdateTime()).build();
    }
}
