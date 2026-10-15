package com.arbor.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.arbor.common.AssertUtils;
import com.arbor.common.PageResult;
import com.arbor.common.ErrorCode;
import com.arbor.common.TenantContext;
import com.arbor.dto.ApiConfigDTO;
import com.arbor.engine.ExecutionLogRecorder;
import com.arbor.engine.FileOutputHandler;
import com.arbor.engine.FlowEngine;
import com.arbor.engine.NodeResult;
import com.arbor.engine.ParamResolver;
import org.springframework.beans.factory.annotation.Value;
import com.arbor.entity.ApiConfig;
import com.arbor.mapper.ApiConfigMapper;
import com.arbor.util.HttpClientUtil;
import com.arbor.util.RedisCacheUtil;
import com.arbor.vo.ApiConfigVO;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 接口配置服务
 *
 * @author gwl
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApiConfigService {

    /** apiId 前缀 */
    private static final String API_ID_PREFIX = "API";
    /** 默认超时（秒） */
    /** @deprecated 请用 defaultTimeout (@Value)，保留做旧代码兼容 */
    private static final int DEFAULT_TIMEOUT = 30;

    /** 解析有效超时：优先用实体/DTO 的值，否则用系统默认 */
    private int resolveTimeout(Integer configured) {
        return configured != null && configured > 0 ? configured : defaultTimeout;
    }

    private final ApiConfigMapper mapper;
    private final GlobalParamService globalParamService;
    private final HttpClientUtil httpClientUtil;
    private final ObjectMapper objectMapper;
    private final RedisCacheUtil redisCacheUtil;
    private final ExecutionLogRecorder executionLogRecorder;
    private final FileOutputHandler fileOutputHandler;
    private final ParamResolver paramResolver;

    @Value("${arbor.api.timeout-seconds:10}")
    private int defaultTimeout;

    /** 接口配置缓存 key 前缀（按用户隔离，多租户插件按 user_id 过滤查询结果） */
    static final String API_CONFIG_CACHE_PREFIX = "api_config:user:";

    private static final java.util.Set<String> ALLOWED_SORT_FIELDS = java.util.Set.of("create_time", "update_time", "id");
    private static final java.util.Set<String> ALLOWED_SORT_ORDERS = java.util.Set.of("ASC", "DESC");

    /**
     * 带缓存的按 ID 查询（key 按用户隔离，避免多租户过滤被缓存穿透）
     * 未命中缓存时回源数据库并写入缓存
     */
    private ApiConfig getByIdCached(Long id) {
        Long userId = TenantContext.getUserId();
        String key = API_CONFIG_CACHE_PREFIX + (userId != null ? userId : 0) + ":id:" + id;
        return redisCacheUtil.get(key, ApiConfig.class)
                .orElseGet(() -> {
                    ApiConfig entity = mapper.selectById(id);
                    if (entity != null) {
                        redisCacheUtil.set(key, entity, RedisCacheUtil.randomizedTtl());
                    }
                    return entity;
                });
    }

    /** 清除某接口配置在所有用户下的缓存副本（数据改动后调用） */
    private void evictCache(Long id) {
        redisCacheUtil.evictByPattern(API_CONFIG_CACHE_PREFIX + "*:id:" + id);
    }

    /** 分页查询 */
    public PageResult<ApiConfigVO> page(int pageNum, int pageSize, String keyword, String sortField, String sortOrder) {
        if (sortField == null || !ALLOWED_SORT_FIELDS.contains(sortField)) sortField = "update_time";
        if (sortOrder == null || !ALLOWED_SORT_ORDERS.contains(sortOrder.toUpperCase())) sortOrder = "DESC";
        else sortOrder = sortOrder.toUpperCase();
        IPage<ApiConfig> page = new Page<>(pageNum, pageSize);
        IPage<ApiConfig> result = mapper.selectPage(page, keyword, sortField, sortOrder);
        List<ApiConfigVO> voList = result.getRecords().stream().map(ApiConfigVO::fromEntity).collect(Collectors.toList());
        return PageResult.of(pageNum, pageSize, result.getTotal(), voList);
    }

    /** 新增（自动生成apiId） */
    @Transactional
    public ApiConfigVO create(ApiConfigDTO dto) {
        ApiConfig entity = new ApiConfig();
        entity.setApiId(generateApiId());
        entity.setName(dto.getName());
        entity.setMethod(dto.getMethod().toUpperCase());
        entity.setUrl(dto.getUrl());
        entity.setTimeout(resolveTimeout(dto.getTimeout()));
        entity.setHeaders(dto.getHeaders());
        entity.setParams(dto.getParams());
        entity.setBodyType(dto.getBodyType());
        entity.setBodyContent(dto.getBodyContent());
        entity.setRemark(dto.getRemark());
        entity.setFlowId(dto.getFlowId());
        entity.setUid(java.util.UUID.randomUUID().toString());
        mapper.insert(entity);
        log.info("新增接口配置: {} (apiId={})", dto.getName(), entity.getApiId());
        return ApiConfigVO.fromEntity(entity);
    }

    /** 修改 */
    @Transactional
    public ApiConfigVO update(Long id, ApiConfigDTO dto) {
        ApiConfig entity = mapper.selectById(id);
        AssertUtils.notNull(entity, ErrorCode.API_CONFIG_NOT_FOUND);
        entity.setName(dto.getName());
        entity.setMethod(dto.getMethod().toUpperCase());
        entity.setUrl(dto.getUrl());
        entity.setTimeout(resolveTimeout(dto.getTimeout()));
        entity.setHeaders(dto.getHeaders());
        entity.setParams(dto.getParams());
        entity.setBodyType(dto.getBodyType());
        entity.setBodyContent(dto.getBodyContent());
        entity.setRemark(dto.getRemark());
        mapper.update(entity);
        log.info("修改接口配置: id={}, name={}", id, dto.getName());
        evictCache(id);
        return ApiConfigVO.fromEntity(entity);
    }

    /** 删除 */
    @Transactional
    public void delete(Long id) {
        ApiConfig entity = mapper.selectById(id);
        AssertUtils.notNull(entity, ErrorCode.API_CONFIG_NOT_FOUND);
        mapper.deleteById(id);
        log.info("删除接口配置: id={}, name={}", id, entity.getName());
        evictCache(id);
    }

    /**
     * 测试接口调用：解析全局参数后发送 HTTP 请求并返回响应
     *
     * @param apiConfigId 接口配置ID
     * @return 响应结果
     */
    public Map<String, Object> testApi(Long apiConfigId) {
        return testApi(apiConfigId, null);
    }

    /**
     * 测试接口调用（带额外变量，供管理页面测试按钮使用，会写 SCRIPT_TEST 日志）
     *
     * @param apiConfigId 接口配置ID
     * @param extraVars   额外变量，可为 null
     * @return 响应结果
     */
    public Map<String, Object> testApi(Long apiConfigId, Map<String, String> extraVars) {
        // 接口测试发生在流程之外，没有前置节点结果，故 previousResults 传 null
        return doExecuteApi(apiConfigId, extraVars, true, null);
    }

    /**
     * 流程引擎调用接口（不写独立日志，由流程级日志统一记录）
     *
     * @param apiConfigId     接口配置ID
     * @param extraVars       流程变量池解析结果，可为 null
     * @param previousResults 前置节点结果，供 ${_input.xxx} / ${node1.field} 引用；无流程上下文时传 null
     * @return 响应结果
     */
    public Map<String, Object> executeForFlow(Long apiConfigId, Map<String, String> extraVars,
                                              Map<String, NodeResult> previousResults) {
        return doExecuteApi(apiConfigId, extraVars, false, previousResults);
    }

    private Map<String, Object> doExecuteApi(Long apiConfigId, Map<String, String> extraVars, boolean writeLog,
                                             Map<String, NodeResult> previousResults) {
        ApiConfig entity = getByIdCached(apiConfigId);
        AssertUtils.notNull(entity, ErrorCode.API_CONFIG_NOT_FOUND);
        // 解析全局参数 + 额外变量
        Map<String, String> resolveParams = buildResolveParams(extraVars);
        // 与 DbScriptService.executeWithScriptDatasource 同源：走 ParamResolver 才能解析
        // ${_input.xxx} / ${node1.field} 这类带点号路径的引用 —— DatasourceService.resolve 只做
        // 扁平的整键查找，遇到 _input.xxx 会直接抛"全局参数不存在"
        String resolvedUrl = paramResolver.resolve(entity.getUrl(), resolveParams, previousResults);
        String resolvedHeaders = paramResolver.resolve(entity.getHeaders(), resolveParams, previousResults);
        String resolvedParams = paramResolver.resolve(entity.getParams(), resolveParams, previousResults);
        String resolvedBody = paramResolver.resolve(entity.getBodyContent(), resolveParams, previousResults);

        // 解析 JSON 格式的 headers/params
        Map<String, String> headerMap = parseJsonToMap(resolvedHeaders);
        Map<String, String> paramMap = parseJsonToMap(resolvedParams);

        log.info("执行接口: apiId={}, {} {}", entity.getApiId(), entity.getMethod(), resolvedUrl);
        long start = System.currentTimeMillis();
        try {
            Map<String, Object> result = httpClientUtil.send(entity.getMethod(), resolvedUrl, headerMap, paramMap,
                    entity.getBodyType(), resolvedBody, resolveTimeout(entity.getTimeout()));
            if (writeLog) {
                executionLogRecorder.save(null, entity.getName(), FlowEngine.SOURCE_SCRIPT_TEST,
                        resolvedUrl, "SUCCESS", System.currentTimeMillis() - start, null);
                return materializeBinary(result);
            }
            return result;
        } catch (Exception e) {
            if (writeLog) {
                executionLogRecorder.save(null, entity.getName(), FlowEngine.SOURCE_SCRIPT_TEST,
                        resolvedUrl, "FAIL", System.currentTimeMillis() - start, e.getMessage());
            }
            throw e;
        }
    }

    /**
     * 测试接口调用（未保存的 DTO，无需事先持久化）
     */
    public Map<String, Object> testApiRaw(ApiConfigDTO dto) {
        return testApiRaw(dto, null);
    }

    /**
     * 测试接口调用（未保存的 DTO，带额外变量）
     */
    public Map<String, Object> testApiRaw(ApiConfigDTO dto, Map<String, String> extraVars) {
        Map<String, String> resolveParams = buildResolveParams(extraVars);
        String resolvedUrl = DatasourceService.resolve(dto.getUrl(), resolveParams);
        String resolvedHeaders = dto.getHeaders() != null ? DatasourceService.resolve(dto.getHeaders(), resolveParams) : "{}";
        String resolvedParams = dto.getParams() != null ? DatasourceService.resolve(dto.getParams(), resolveParams) : "{}";
        String resolvedBody = dto.getBodyContent() != null ? DatasourceService.resolve(dto.getBodyContent(), resolveParams) : "";

        Map<String, String> headerMap = parseJsonToMap(resolvedHeaders);
        Map<String, String> paramMap = parseJsonToMap(resolvedParams);
        int timeout = resolveTimeout(dto.getTimeout());

        log.info("测试接口(未保存): {} {}", dto.getMethod(), resolvedUrl);
        long start = System.currentTimeMillis();
        try {
            Map<String, Object> result = httpClientUtil.send(dto.getMethod(), resolvedUrl, headerMap, paramMap,
                    dto.getBodyType(), resolvedBody, timeout);
            executionLogRecorder.save(null, dto.getName(), FlowEngine.SOURCE_SCRIPT_TEST,
                    resolvedUrl, "SUCCESS", System.currentTimeMillis() - start, null);
            return materializeBinary(result);
        } catch (Exception e) {
            executionLogRecorder.save(null, dto.getName(), FlowEngine.SOURCE_SCRIPT_TEST,
                    resolvedUrl, "FAIL", System.currentTimeMillis() - start, e.getMessage());
            throw e;
        }
    }

    /**
     * 测试场景的二进制响应处理：落库为输出文件，body 替换为摘要并携带 fileId（供前端下载），
     * 避免 byte[] 被序列化为 base64 回传前端。流程引擎路径（executeForFlow）不走此方法，
     * 由 RefApiNodeExecutor 自行拿字节落库。
     */
    private Map<String, Object> materializeBinary(Map<String, Object> result) {
        if (!(result.get("body") instanceof byte[] bytes)) return result;
        var info = fileOutputHandler.saveBinary(bytes,
                (String) result.get("fileName"), (String) result.get("contentType"), 0L);
        result.remove("binary");
        if (info != null) {
            result.put("body", "[二进制文件响应] " + info.getFileName() + " (" + info.getSizeText() + ")");
            result.put("fileId", info.getFileId());
            result.put("fileName", info.getFileName());
        } else {
            result.put("body", "[二进制文件响应，保存文件失败]");
        }
        return result;
    }

    /** 构建解析参数：全局参数 + 额外变量（变量池），额外变量优先级高于全局参数 */
    private Map<String, String> buildResolveParams(Map<String, String> extraVars) {
        Map<String, String> params = new HashMap<>(globalParamService.getAllParams());
        if (extraVars != null && !extraVars.isEmpty()) {
            params.putAll(extraVars);
        }
        return params;
    }

    /**
     * 保存响应示例
     */
    @Transactional
    public void saveResponseExample(Long apiConfigId, String responseExample) {
        ApiConfig entity = mapper.selectById(apiConfigId);
        AssertUtils.notNull(entity, ErrorCode.API_CONFIG_NOT_FOUND);
        entity.setResponseExample(responseExample);
        mapper.update(entity);
        log.info("保存接口响应示例: apiId={}", entity.getApiId());
        evictCache(apiConfigId);
    }

    /** 生成唯一 apiId */
    private String generateApiId() {
        return API_ID_PREFIX + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
    }

    /** 将 JSON 字符串解析为 Map */
    private Map<String, String> parseJsonToMap(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, String>>() {});
        } catch (Exception e) {
            log.warn("解析JSON失败: {}", json, e);
            return Collections.emptyMap();
        }
    }
}
