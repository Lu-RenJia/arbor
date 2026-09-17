package com.arbor.engine.executor;

import com.arbor.engine.NodeExecutionContext;
import com.arbor.engine.NodeExecutor;
import com.arbor.engine.NodeResult;
import com.arbor.entity.RedisScript;
import com.arbor.service.RedisScriptService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * REDIS 节点执行器（仅配置模式）
 * <p>
 * 按 {@code config.redisConfigId} 指向的 redis_script 配置行执行，key 由
 * {@link RedisScriptService#buildBusinessKey} 拼成 {@code {业务前缀}:{redisKey}}。
 * GET / GET&amp;SET 未命中返回 ok(null)，GET&amp;SET 的分支与回填 SET 由引擎处理。
 * </p>
 * <p>
 * 原先的「手填模式」（无 redisConfigId，直接读写平台系统 Redis）已废弃（D3）：
 * 它写的是平台自身缓存库（与 {@code redis_script:user:*}、{@code flow_nodes:*} 混库），
 * 而配置模式写的是用户登记的业务 Redis，同一个节点类型不应该有两套存储语义；
 * 且它与「内联配置」职责完全重叠。存量手填节点已由 V24 迁移为内联配置。
 * </p>
 *
 * @author gwl
 */
@Slf4j
@Component
public class RedisNodeExecutor implements NodeExecutor {

    public static final String NODE_TYPE = "REDIS";

    private final RedisScriptService redisScriptService;
    private final ObjectMapper objectMapper;

    public RedisNodeExecutor(RedisScriptService redisScriptService, ObjectMapper objectMapper) {
        this.redisScriptService = redisScriptService;
        this.objectMapper = objectMapper;
    }

    @Override public String getNodeType() { return NODE_TYPE; }

    @Override
    public NodeResult execute(NodeExecutionContext context) {
        try {
            Map<String, Object> config = objectMapper.readValue(context.getConfig(), Map.class);
            Long cfgId = toLong(config.get("redisConfigId"));
            if (cfgId == null) {
                // 手填模式已废弃，没选配置就无法确定数据源与业务前缀，直接报清楚而不是默默写错地方
                return NodeResult.fail("Redis 节点未选择 Redis 配置，请先在节点属性里选择或新建一个配置");
            }
            return executeByConfig(cfgId, context);
        } catch (Exception e) {
            log.error("REDIS 失败", e);
            return NodeResult.fail("Redis 节点执行失败: " + e.getMessage());
        }
    }

    /** 按 redis_script 配置行执行（key 解析 ${} 后拼业务前缀） */
    private NodeResult executeByConfig(Long cfgId, NodeExecutionContext context) throws Exception {
        RedisScript script = redisScriptService.getScript(cfgId);
        if (script == null) {
            return NodeResult.fail("Redis配置不存在: id=" + cfgId);
        }
        String resolvedKey = redisScriptService.resolveScriptKey(script, context.getGlobalParams(), context.getPreviousResults());
        String resolvedValue = redisScriptService.resolveScriptValue(script, context.getGlobalParams(), context.getPreviousResults());
        String fullKey = redisScriptService.buildBusinessKey(script, resolvedKey);
        Object result = redisScriptService.executeResolved(script, fullKey, resolvedValue);

        String cmd = script.getCommand().toUpperCase();
        if ("GET".equals(cmd) || RedisScriptService.CMD_GET_SET.equals(cmd)) {
            if (result == null) {
                log.info("Redis {} 未命中: key={}", cmd, fullKey);
                return NodeResult.ok(null); // 未命中返回 null，GET&SET 由 FlowEngine 决定是否走分支
            }
            log.info("Redis {} 命中: key={}", cmd, fullKey);
            String cached = (String) result;
            try { return NodeResult.ok(objectMapper.readValue(cached, Object.class)); } catch (Exception e) { return NodeResult.ok(cached); }
        }
        return NodeResult.ok(result);
    }

    private Long toLong(Object v) {
        if (v instanceof Number) return ((Number) v).longValue();
        if (v instanceof String) try { return Long.parseLong((String) v); } catch (Exception ignored) {}
        return null;
    }
}
