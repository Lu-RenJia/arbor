package com.arbor.engine;

import com.arbor.entity.RedisScript;
import com.arbor.service.RedisScriptService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * REDIS 节点 GET&amp;SET 组合命令的引擎侧支撑：
 * 判定节点是否为 GET&amp;SET 配置（决定未命中时是否走分支），
 * 以及分支执行完成后的结果回写 + 缓存回填。
 * FlowEngine（DB 路径）、FlowDtoExecutionService（DTO 路径）与 SubFlowExecutor（子流程路径）共用，避免三写漂移。
 *
 * @author gwl
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisGetSetSupport {

    private final RedisScriptService redisScriptService;
    private final ObjectMapper objectMapper;

    /**
     * 从节点 config JSON 中取 redisConfigId 并加载配置行。
     *
     * @param nodeConfig 节点原始 config JSON 字符串
     * @return 配置行；无 redisConfigId 或配置不存在返回 null
     */
    public RedisScript loadScript(String nodeConfig) {
        Map<String, Object> config = parseConfig(nodeConfig);
        if (config == null) return null;
        try {
            Object v = config.get("redisConfigId");
            Long cfgId = null;
            if (v instanceof Number n) cfgId = n.longValue();
            else if (v instanceof String s && !s.isBlank()) cfgId = Long.parseLong(s);
            return redisScriptService.getScript(cfgId);
        } catch (Exception e) {
            log.warn("加载 REDIS 配置行失败: {}", e.getMessage());
            return null;
        }
    }

    /** 是否 GET&amp;SET 命令的配置行（只有它才允许未命中走分支） */
    public boolean isGetSet(RedisScript script) {
        return script != null && RedisScriptService.CMD_GET_SET.equalsIgnoreCase(script.getCommand());
    }

    /** 节点是否为 GET&amp;SET（决定缓存未命中时是否走分支）：看配置行命令，未选配置一律 false */
    public boolean isGetSetNode(String nodeConfig) {
        return isGetSet(loadScript(nodeConfig));
    }

    /**
     * GET&amp;SET 节点的有效过期秒数：取配置行 TTL（含单位换算）。
     * &lt;=0 视为无效（引擎据此拒绝执行分支流程）；未选配置返回 0。
     */
    public long getSetTtlSeconds(String nodeConfig) {
        RedisScript script = loadScript(nodeConfig);
        return script != null ? RedisScriptService.ttlToSeconds(script.getTtl(), script.getTtlUnit()) : 0L;
    }

    /**
     * 分支执行完成后：把分支结果回写为 REDIS 节点的结果，并按配置行的 key/ttl 回填缓存。
     * 支持可选 cacheExtractPath 配置：按点号路径从分支结果中提取字段后再回填（如 body.access_token）。
     * 回写必须作用于 previousResults / nodeDetailList 中的同一 NodeResult 实例（同引用两边生效）。
     * 缓存回填失败仅告警，不影响流程结果。
     *
     * @param nodeConfig      REDIS 节点原始 config JSON 字符串
     * @param redisNodeResult REDIS 节点的原始 NodeResult 实例（直接 setData 回写）
     * @param finalData       最后一个分支节点的结果数据
     * @param globalParams    全局参数
     * @param previousResults 前置节点结果（解析配置 key 中的 ${}）
     */
    public void writeBackAndCache(String nodeConfig, NodeResult redisNodeResult, Object finalData,
                                  Map<String, String> globalParams, Map<String, NodeResult> previousResults) {
        // 按配置路径提取分支结果中的字段（如 body.access_token）
        Object cacheData = applyExtractPath(finalData, nodeConfig);
        redisNodeResult.setData(cacheData);
        try {
            RedisScript script = loadScript(nodeConfig);
            if (script == null) {
                log.warn("Redis GET&SET 回填跳过：节点未关联 Redis 配置");
                return;
            }
            String resolvedKey = redisScriptService.resolveScriptKey(script, globalParams, previousResults);
            // 必须与 GET 侧同走 buildBusinessKey：两边规则不一致会导致回填的 key 永远读不到
            String fullKey = redisScriptService.buildBusinessKey(script, resolvedKey);
            redisScriptService.setCacheValue(script, fullKey, cacheData);
        } catch (Exception e) {
            log.warn("Redis GET&SET 缓存回填失败（不影响流程结果）: {}", e.getMessage(), e);
        }
    }

    /**
     * 若节点配置中有 cacheExtractPath，按点号路径从分支结果中提取字段。
     * 例如 finalData={"body":{"access_token":"abc"}, "statusCode":200}，路径 body.access_token → "abc"
     * 未配置路径或提取失败时返回原始数据。
     */
    private Object applyExtractPath(Object data, String nodeConfig) {
        Map<String, Object> config = parseConfig(nodeConfig);
        if (config == null) return data;
        Object pathObj = config.get("cacheExtractPath");
        if (pathObj == null || String.valueOf(pathObj).isBlank()) return data;
        String path = String.valueOf(pathObj).trim();
        try {
            Object current = data;
            for (String field : path.split("\\.")) {
                if (current instanceof Map<?, ?> map) {
                    current = map.get(field);
                } else if (current != null) {
                    // 尝试用 Jackson 转为 Map 再取
                    Map<?, ?> map = objectMapper.convertValue(current, Map.class);
                    current = map.get(field);
                }
                if (current == null) {
                    log.warn("Redis GET&SET 提取路径 '{}' 在字段 '{}' 处为 null，回填原始数据", path, field);
                    return data;
                }
            }
            log.info("Redis GET&SET 提取路径 '{}' → {}", path, current);
            return current;
        } catch (Exception e) {
            log.warn("Redis GET&SET 提取路径 '{}' 失败，回填原始数据: {}", path, e.getMessage());
            return data;
        }
    }

    /** 解析节点 config JSON；空或非法返回 null */
    private Map<String, Object> parseConfig(String nodeConfig) {
        if (nodeConfig == null || nodeConfig.isBlank()) return null;
        try {
            return objectMapper.readValue(nodeConfig, Map.class);
        } catch (Exception e) {
            log.warn("解析 REDIS 节点配置失败: {}", e.getMessage());
            return null;
        }
    }
}
