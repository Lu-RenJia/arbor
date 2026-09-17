package com.arbor.controller;

import com.arbor.common.EnvironmentContext;
import com.arbor.common.PageResult;
import com.arbor.common.Result;
import com.arbor.dto.ApiConfigDTO;
import com.arbor.entity.Environment;
import com.arbor.service.ApiConfigService;
import com.arbor.service.EnvironmentService;
import com.arbor.service.FlowVariableService;
import com.arbor.vo.ApiConfigVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 接口配置控制器
 *
 * @author gwl
 */
@Slf4j
@RestController
@RequestMapping("/api/api-config")
@RequiredArgsConstructor
public class ApiConfigController {

    private final ApiConfigService service;
    private final EnvironmentService environmentService;
    private final FlowVariableService flowVariableService;

    /** 分页查询 */
    @GetMapping("/page")
    public Result<PageResult<ApiConfigVO>> page(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String sortField,
            @RequestParam(required = false) String sortOrder) {
        return Result.ok(service.page(pageNum, pageSize, keyword, sortField, sortOrder));
    }

    /** 新增（自动生成apiId） */
    @PostMapping
    public Result<ApiConfigVO> create(@Valid @RequestBody ApiConfigDTO dto) {
        return Result.ok("添加成功", service.create(dto));
    }

    /** 修改 */
    @PutMapping("/{id}")
    public Result<ApiConfigVO> update(@PathVariable Long id, @Valid @RequestBody ApiConfigDTO dto) {
        return Result.ok("修改成功", service.update(id, dto));
    }

    /** 删除 */
    @DeleteMapping("/{id}")
    public Result<String> delete(@PathVariable Long id) {
        service.delete(id);
        return Result.ok("删除成功");
    }

    /** 测试接口：解析全局参数后发送 HTTP 请求并返回响应（可选指定环境） */
    @PostMapping("/test/{id}")
    public Result<Map<String, Object>> test(@PathVariable Long id,
                                            @RequestParam(required = false) Long envId) {
        return withEnv(envId, () -> service.testApi(id));
    }

    /** 测试接口（未保存）：传入配置直接发送请求，无需保存（可选指定环境 + 流程变量池） */
    @PostMapping("/test")
    public Result<Map<String, Object>> testRaw(@Valid @RequestBody ApiConfigDTO dto,
                                               @RequestParam(required = false) Long envId,
                                               @RequestParam(required = false) Long flowId) {
        return withEnv(envId, () -> service.testApiRaw(dto, resolveFlowVars(flowId)));
    }

    /** 设置环境上下文后执行测试，确保全局参数按指定环境解析 */
    private Result<Map<String, Object>> withEnv(Long envId, java.util.function.Supplier<Map<String, Object>> action) {
        if (envId != null) {
            Environment env = environmentService.getByIdCached(envId);
            if (env != null) {
                EnvironmentContext.set(envId, env.getCode());
            }
        }
        try {
            return Result.ok(action.get());
        } finally {
            if (envId != null) {
                EnvironmentContext.clear();
            }
        }
    }

    /** 解析流程变量池（供设计器内测试用，无执行上下文时 previousResults=null，宽松模式） */
    private Map<String, String> resolveFlowVars(Long flowId) {
        if (flowId == null) return null;
        try {
            Map<String, Object> vars = flowVariableService.resolveVariables(flowId, null, true);
            Map<String, String> strVars = new HashMap<>();
            vars.forEach((k, v) -> {
                if (v == null) return;
                if (v instanceof String s) { strVars.put(k, s); }
                else if (v instanceof Number || v instanceof Boolean) { strVars.put(k, v.toString()); }
                else {
                    try { strVars.put(k, new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(v)); }
                    catch (Exception e) { strVars.put(k, String.valueOf(v)); }
                }
            });
            return strVars;
        } catch (Exception e) {
            log.warn("测试接口解析变量池失败（忽略）: {}", e.getMessage());
            return null;
        }
    }

    /** 保存响应示例 */
    @PostMapping("/save-example/{id}")
    public Result<String> saveExample(@PathVariable Long id, @RequestBody String responseExample) {
        service.saveResponseExample(id, responseExample);
        return Result.ok("保存成功");
    }
}
