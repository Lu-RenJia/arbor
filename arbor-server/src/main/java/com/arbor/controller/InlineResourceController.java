package com.arbor.controller;

import com.arbor.common.AssertUtils;
import com.arbor.common.ErrorCode;
import com.arbor.common.Result;
import com.arbor.entity.ApiConfig;
import com.arbor.entity.DbScript;
import com.arbor.entity.ProgramScript;
import com.arbor.entity.RedisScript;
import com.arbor.mapper.ApiConfigMapper;
import com.arbor.mapper.DbScriptMapper;
import com.arbor.mapper.ProgramScriptMapper;
import com.arbor.mapper.RedisScriptMapper;
import com.arbor.service.RedisScriptService;
import com.arbor.vo.RedisScriptVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 内联资源管理控制器
 * 支持内联↔引用模式转换：
 * - promote：内联→全局（移除 flowId 绑定，资源出现在全局列表）
 * - demote：全局→内联（复制一份绑定到指定流程，原全局资源不变）
 *
 * @author gwl
 */
@Slf4j
@RestController
@RequestMapping("/api/inline-resource")
@RequiredArgsConstructor
public class InlineResourceController {

    private final DbScriptMapper dbScriptMapper;
    private final ProgramScriptMapper programScriptMapper;
    private final ApiConfigMapper apiConfigMapper;
    private final RedisScriptMapper redisScriptMapper;
    private final RedisScriptService redisScriptService;

    /**
     * 查询指定流程的所有内联资源（供前端加载内联资源名称/编辑用）
     * <p>
     * Redis 一支返回 VO 而非实体：属性面板的摘要卡要展示完整 key
     * （{@code {业务前缀}:{redisKey}}），裸实体只有 prefixId，前端拼不出来。
     * </p>
     */
    @GetMapping("/flow/{flowId}")
    public Result<Map<String, Object>> listByFlow(@PathVariable Long flowId) {
        List<DbScript> dbScripts = dbScriptMapper.selectByFlowId(flowId);
        List<ProgramScript> programScripts = programScriptMapper.selectByFlowId(flowId);
        List<ApiConfig> apiConfigs = apiConfigMapper.selectByFlowId(flowId);
        List<RedisScriptVO> redisScripts = redisScriptService.listVOByFlow(flowId);
        return Result.ok(Map.of(
                "dbScripts", dbScripts,
                "programScripts", programScripts,
                "apiConfigs", apiConfigs,
                "redisScripts", redisScripts
        ));
    }

    /**
     * 内联→全局：解除 flowId 绑定，资源变为全局可见
     *
     * @param type 资源类型：db_script / program_script / api_config / redis_script
     * @param id   资源ID
     */
    @PutMapping("/promote/{type}/{id}")
    @Transactional
    public Result<String> promote(@PathVariable String type, @PathVariable Long id) {
        switch (type) {
            case "db", "db_script" -> {
                DbScript entity = dbScriptMapper.selectById(id);
                AssertUtils.notNull(entity, ErrorCode.DB_SCRIPT_NOT_FOUND);
                entity.setFlowId(null);
                dbScriptMapper.update(entity);
            }
            case "ps", "program_script" -> {
                ProgramScript entity = programScriptMapper.selectById(id);
                AssertUtils.notNull(entity, ErrorCode.PROGRAM_SCRIPT_NOT_FOUND);
                entity.setFlowId(null);
                programScriptMapper.update(entity);
            }
            case "api", "api_config" -> {
                ApiConfig entity = apiConfigMapper.selectById(id);
                AssertUtils.notNull(entity, ErrorCode.API_CONFIG_NOT_FOUND);
                entity.setFlowId(null);
                apiConfigMapper.update(entity);
            }
            case "redis", "redis_script" -> {
                RedisScript entity = redisScriptMapper.selectById(id);
                AssertUtils.notNull(entity, ErrorCode.REDIS_SCRIPT_NOT_FOUND);
                entity.setFlowId(null);
                redisScriptMapper.update(entity);
            }
            default -> {
                return Result.fail("不支持的资源类型: " + type);
            }
        }
        log.info("内联资源提升为全局: type={}, id={}", type, id);
        return Result.ok("已转为全局资源");
    }

    /**
     * 全局→内联：复制一份资源绑定到指定流程（原全局资源不变）
     *
     * @param type 资源类型：db_script / program_script / api_config / redis_script
     * @param id   原全局资源ID
     * @param body 请求体含 flowId（目标流程ID）
     * @return 新创建的内联资源ID
     */
    @PostMapping("/demote/{type}/{id}")
    @Transactional
    public Result<Map<String, Object>> demote(@PathVariable String type, @PathVariable Long id,
                                              @RequestBody Map<String, Long> body) {
        Long flowId = body.get("flowId");
        if (flowId == null) return Result.fail("缺少 flowId");
        Map<String, Object> result = new java.util.HashMap<>();
        switch (type) {
            case "db", "db_script" -> {
                DbScript src = dbScriptMapper.selectById(id);
                AssertUtils.notNull(src, ErrorCode.DB_SCRIPT_NOT_FOUND);
                DbScript copy = new DbScript();
                copy.setName(src.getName());
                copy.setDatasourceId(src.getDatasourceId());
                copy.setSqlContent(src.getSqlContent());
                copy.setReturnLimit(src.getReturnLimit());
                copy.setQueryTimeout(src.getQueryTimeout());
                copy.setRemark(src.getRemark());
                copy.setFlowId(flowId);
                copy.setUid(java.util.UUID.randomUUID().toString());
                dbScriptMapper.insert(copy);
                result.put("id", copy.getId());
            }
            case "ps", "program_script" -> {
                ProgramScript src = programScriptMapper.selectById(id);
                AssertUtils.notNull(src, ErrorCode.PROGRAM_SCRIPT_NOT_FOUND);
                ProgramScript copy = new ProgramScript();
                String newScriptId = java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 12);
                copy.setScriptId(newScriptId);
                copy.setName(src.getName());
                copy.setScriptContent(src.getScriptContent());
                copy.setQueryTimeout(src.getQueryTimeout());
                copy.setRemark(src.getRemark());
                copy.setFlowId(flowId);
                copy.setUid(java.util.UUID.randomUUID().toString());
                programScriptMapper.insert(copy);
                result.put("id", copy.getId());
                result.put("scriptId", newScriptId);
            }
            case "api", "api_config" -> {
                ApiConfig src = apiConfigMapper.selectById(id);
                AssertUtils.notNull(src, ErrorCode.API_CONFIG_NOT_FOUND);
                ApiConfig copy = new ApiConfig();
                copy.setApiId(java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 12));
                copy.setName(src.getName());
                copy.setMethod(src.getMethod());
                copy.setUrl(src.getUrl());
                copy.setTimeout(src.getTimeout());
                copy.setHeaders(src.getHeaders());
                copy.setParams(src.getParams());
                copy.setBodyType(src.getBodyType());
                copy.setBodyContent(src.getBodyContent());
                copy.setRemark(src.getRemark());
                copy.setFlowId(flowId);
                copy.setUid(java.util.UUID.randomUUID().toString());
                apiConfigMapper.insert(copy);
                result.put("id", copy.getId());
            }
            case "redis", "redis_script" -> {
                RedisScript src = redisScriptMapper.selectById(id);
                AssertUtils.notNull(src, ErrorCode.REDIS_SCRIPT_NOT_FOUND);
                RedisScript copy = new RedisScript();
                copy.setName(src.getName());
                copy.setDatasourceId(src.getDatasourceId());
                copy.setPrefixId(src.getPrefixId());
                copy.setCommand(src.getCommand());
                copy.setRedisKey(src.getRedisKey());
                copy.setRedisValue(src.getRedisValue());
                copy.setTtl(src.getTtl());
                copy.setTtlUnit(src.getTtlUnit());
                copy.setRemark(src.getRemark());
                copy.setFlowId(flowId);
                copy.setUid(java.util.UUID.randomUUID().toString());
                redisScriptMapper.insert(copy);
                result.put("id", copy.getId());
            }
            default -> {
                return Result.fail("不支持的资源类型: " + type);
            }
        }
        log.info("全局资源复制为内联: type={}, srcId={}, flowId={}, result={}", type, id, flowId, result);
        return Result.ok(result);
    }
}
