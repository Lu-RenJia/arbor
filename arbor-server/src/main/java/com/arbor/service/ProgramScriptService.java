package com.arbor.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.arbor.common.AssertUtils;
import com.arbor.common.ErrorCode;
import com.arbor.common.PageResult;
import com.arbor.common.TenantContext;
import com.arbor.dto.ProgramScriptDTO;
import com.arbor.engine.ExecutionLogRecorder;
import com.arbor.engine.FlowEngine;
import com.arbor.engine.NodeResult;
import com.arbor.entity.ProgramScript;
import com.arbor.groovy.GroovyScriptEngine;
import com.arbor.mapper.ProgramScriptMapper;
import com.arbor.util.RedisCacheUtil;
import com.arbor.vo.ProgramScriptVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

/**
 * 程序脚本服务
 *
 * @author gwl
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProgramScriptService {

    /** scriptId 前缀 */
    private static final String SCRIPT_ID_PREFIX = "PS";

    /** 程序脚本缓存 key 前缀（按用户隔离） */
    static final String PS_CACHE_PREFIX = "program_script:user";

    private static final java.util.Set<String> ALLOWED_SORT_FIELDS = java.util.Set.of("create_time", "update_time", "id");
    private static final java.util.Set<String> ALLOWED_SORT_ORDERS = java.util.Set.of("ASC", "DESC");

    private String psCacheKey(String scriptId) {
        Long userId = TenantContext.getUserId();
        return RedisCacheUtil.buildKey(PS_CACHE_PREFIX, (userId != null ? userId : 0), "scriptId", scriptId);
    }

    private final ProgramScriptMapper mapper;
    private final GlobalParamService globalParamService;
    private final FlowVariableService flowVariableService;
    private final GroovyScriptEngine groovyScriptEngine;
    private final com.arbor.util.RedisCacheUtil redisCacheUtil;
    private final ExecutionLogRecorder executionLogRecorder;

    @Value("${arbor.groovy.timeout-seconds:60}")
    private int defaultTimeout;

    /** 分页查询 */
    public PageResult<ProgramScriptVO> page(int pageNum, int pageSize, String keyword, String sortField, String sortOrder) {
        if (sortField == null || !ALLOWED_SORT_FIELDS.contains(sortField)) sortField = "update_time";
        if (sortOrder == null || !ALLOWED_SORT_ORDERS.contains(sortOrder.toUpperCase())) sortOrder = "DESC";
        else sortOrder = sortOrder.toUpperCase();
        IPage<ProgramScript> page = new Page<>(pageNum, pageSize);
        IPage<ProgramScript> result = mapper.selectPage(page, keyword, sortField, sortOrder);
        List<ProgramScriptVO> voList = result.getRecords().stream().map(ProgramScriptVO::fromEntity).collect(Collectors.toList());
        return PageResult.of(pageNum, pageSize, result.getTotal(), voList);
    }

    /** 新增（自动生成scriptId） */
    @Transactional
    public ProgramScriptVO create(ProgramScriptDTO dto) {
        ProgramScript entity = new ProgramScript();
        entity.setScriptId(generateScriptId());
        entity.setName(dto.getName());
        entity.setScriptContent(dto.getScriptContent());
        entity.setQueryTimeout(dto.getQueryTimeout());
        entity.setRemark(dto.getRemark());
        entity.setFlowId(dto.getFlowId());
        entity.setUid(java.util.UUID.randomUUID().toString());
        mapper.insert(entity);
        log.info("新增程序脚本: {} (scriptId={})", dto.getName(), entity.getScriptId());
        return ProgramScriptVO.fromEntity(entity);
    }

    /** 修改 */
    @Transactional
    public ProgramScriptVO update(Long id, ProgramScriptDTO dto) {
        ProgramScript entity = mapper.selectById(id);
        AssertUtils.notNull(entity, ErrorCode.PROGRAM_SCRIPT_NOT_FOUND, id);
        entity.setName(dto.getName());
        entity.setScriptContent(dto.getScriptContent());
        entity.setRemark(dto.getRemark());
        mapper.update(entity);
        // 驱逐编译缓存 + Redis 缓存
        groovyScriptEngine.evictCache(entity.getScriptId());
        redisCacheUtil.evict(psCacheKey(entity.getScriptId()));
        log.info("修改程序脚本: id={}, name={}, scriptId={}", id, dto.getName(), entity.getScriptId());
        return ProgramScriptVO.fromEntity(entity);
    }

    /** 删除 */
    @Transactional
    public void delete(Long id) {
        ProgramScript entity = mapper.selectById(id);
        AssertUtils.notNull(entity, ErrorCode.PROGRAM_SCRIPT_NOT_FOUND, id);
        mapper.deleteById(id);
        // 驱逐编译缓存 + Redis 缓存
        groovyScriptEngine.evictCache(entity.getScriptId());
        redisCacheUtil.evict(psCacheKey(entity.getScriptId()));
        log.info("删除程序脚本: id={}, name={}, scriptId={}", id, entity.getName(), entity.getScriptId());
    }

    /**
     * 带缓存的按 scriptId 查询
     */
    private ProgramScript getByScriptIdCached(String scriptId) {
        String key = psCacheKey(scriptId);
        return redisCacheUtil.get(key, ProgramScript.class)
                .orElseGet(() -> {
                    ProgramScript entity = mapper.selectByScriptId(scriptId);
                    if (entity != null) {
                        redisCacheUtil.set(key, entity, com.arbor.util.RedisCacheUtil.randomizedTtl());
                    }
                    return entity;
                });
    }

    /**
     * 独立运行 Groovy 脚本（无前置节点），注入全局参数为变量
     */
    public Object runScript(String scriptId) {
        ProgramScript entity = getByScriptIdCached(scriptId);
        AssertUtils.notNull(entity, ErrorCode.PROGRAM_SCRIPT_NOT_FOUND, scriptId);
        Map<String, Object> variables = new HashMap<>(globalParamService.getAllParams());
        int timeout = entity.getQueryTimeout() != null && entity.getQueryTimeout() > 0
                ? entity.getQueryTimeout() : defaultTimeout;
        log.info("运行程序脚本: scriptId={}, name={}, timeout={}s", scriptId, entity.getName(), timeout);
        return executeWithTimeout(scriptId, entity.getScriptContent(), variables, timeout,
                entity.getName(), entity.getScriptContent());
    }

    /**
     * 流程引擎内部调用：从变量池解析变量后注入执行
     */
    public Object runScript(String scriptId, Long flowId,
                            Map<String, NodeResult> previousResults) {
        ProgramScript entity = getByScriptIdCached(scriptId);
        AssertUtils.notNull(entity, ErrorCode.PROGRAM_SCRIPT_NOT_FOUND, scriptId);
        Map<String, Object> variables = flowVariableService.resolveVariables(flowId, previousResults);
        log.info("运行程序脚本(流程引擎): scriptId={}, flowId={}, 变量数={}",
                scriptId, flowId, variables.size());
        return groovyScriptEngine.execute(scriptId, entity.getScriptContent(), variables);
    }

    /**
     * 测试脚本：直接执行脚本内容，注入全局参数为变量
     */
    public Object testScript(String scriptContent) {
        Map<String, Object> variables = new HashMap<>(globalParamService.getAllParams());
        log.info("测试脚本执行, 长度={}, timeout={}s", scriptContent.length(), defaultTimeout);
        return executeWithTimeout("test", scriptContent, variables, defaultTimeout, null, scriptContent);
    }

    /**
     * 带超时控制的脚本执行，超时抛 BusinessException。
     * 用 CompletableFuture 包装 Groovy 执行，超时后中断等待。
     */
    private Object executeWithTimeout(String scriptId, String scriptContent,
                                       Map<String, Object> variables, int timeoutSec,
                                       String logName, String logInput) {
        long start = System.currentTimeMillis();
        try {
            // 异步线程传递租户/环境上下文：提交前捕获快照，任务体内恢复，结束后清理防串台
            com.arbor.common.ContextSnapshot snapshot = com.arbor.common.ContextSnapshot.capture();
            CompletableFuture<Object> future = CompletableFuture.supplyAsync(
                    snapshot.wrap(() -> groovyScriptEngine.execute(scriptId, scriptContent, variables)));
            Object result = future.get(timeoutSec, TimeUnit.SECONDS);
            executionLogRecorder.save(null, logName, FlowEngine.SOURCE_SCRIPT_TEST,
                    logInput, "SUCCESS", System.currentTimeMillis() - start, null);
            return result;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            String msg = "Groovy 脚本执行被中断";
            log.warn(msg);
            executionLogRecorder.save(null, logName, FlowEngine.SOURCE_SCRIPT_TEST,
                    logInput, "FAIL", System.currentTimeMillis() - start, msg);
            throw new com.arbor.common.BusinessException(ErrorCode.GROOVY_SCRIPT_FAILED, msg);
        } catch (java.util.concurrent.ExecutionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            executionLogRecorder.save(null, logName, FlowEngine.SOURCE_SCRIPT_TEST,
                    logInput, "FAIL", System.currentTimeMillis() - start, cause.getMessage());
            if (cause instanceof RuntimeException re) throw re;
            throw new com.arbor.common.BusinessException(ErrorCode.GROOVY_SCRIPT_FAILED, cause.getMessage());
        } catch (TimeoutException e) {
            String msg = "Groovy 脚本执行超时（" + timeoutSec + "秒）";
            log.warn(msg);
            executionLogRecorder.save(null, logName, FlowEngine.SOURCE_SCRIPT_TEST,
                    logInput, "FAIL", System.currentTimeMillis() - start, msg);
            throw new com.arbor.common.BusinessException(ErrorCode.GROOVY_SCRIPT_FAILED, msg);
        }
    }

    /** 生成唯一 scriptId */
    private String generateScriptId() {
        return SCRIPT_ID_PREFIX + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
    }
}
