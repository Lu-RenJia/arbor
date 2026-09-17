package com.arbor.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Redis 业务前缀使用情况（D11）—— 某前缀被哪些流程/节点使用
 * <p>
 * 引用链路：{@code redis_key_prefix} ← {@code redis_script.prefix_id} ←
 * {@code flow_node.config} 里的 JSON 字段 {@code redisConfigId} ← {@code flow}。
 * 第二跳是藏在 JSON 字符串里的软引用，无外键无索引，靠
 * {@code FlowNodeMapper.selectByConfigRef} 反查。
 * </p>
 * <p>
 * {@code flowCount} 按 flowId 去重、{@code nodeCount} 不去重，两者语义不同
 * （同一流程两个节点用同一前缀 → flowCount=1、nodeCount=2），前端须分别标注，
 * 不可合并成一个「使用次数」。
 * </p>
 */
@Data
@Builder
public class RedisPrefixUsageVO {

    private Long prefixId;
    /** 前缀值 */
    private String prefix;
    /** 前缀名称 */
    private String prefixName;
    /** 所属 Redis 数据源ID */
    private Long datasourceId;
    /** 引用该前缀的 Redis 配置数 */
    private Integer scriptCount;
    /** 涉及流程数（按 flowId 去重） */
    private Integer flowCount;
    /** 涉及节点数（不去重） */
    private Integer nodeCount;
    /** 使用明细，按 flowId + nodeId 排序 */
    private List<Item> items;

    @Data
    @Builder
    public static class Item {
        private Long flowId;
        private String flowName;
        private Long nodeId;
        private String nodeCode;
        private String nodeName;
        private Long scriptId;
        private String scriptName;
        /** GET/SET/DEL/EXPIRE/TTL/EXISTS/GET&SET */
        private String command;
        /**
         * 最终落地的完整 key = {@code {prefix}:{redisKey}}。
         * 这里用**未解析**的 redisKey（保留 {@code ${}} 原样），因为统计页拿不到运行时变量值；
         * 拼接直接调 {@code RedisScriptService.buildBusinessKey} 静态版，与执行期同一实现。
         */
        private String fullKey;
        /** true=内联配置（script.flowId 非空，专属某流程），false=全局配置 */
        private Boolean inline;
    }

    /** 无任何使用时的空结果（前缀存在但没被引用） */
    public static RedisPrefixUsageVO empty(RedisPrefixUsageVO.RedisPrefixUsageVOBuilder b) {
        return b.scriptCount(0).flowCount(0).nodeCount(0).items(List.of()).build();
    }
}
