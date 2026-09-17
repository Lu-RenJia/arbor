package com.arbor.controller;

import com.arbor.common.PageResult;
import com.arbor.common.Result;
import com.arbor.dto.DbScriptDTO;
import com.arbor.service.DbScriptService;
import com.arbor.vo.DbScriptVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 数据库脚本控制器
 *
 * @author gwl
 */
@Slf4j
@RestController
@RequestMapping("/api/db-script")
@RequiredArgsConstructor
public class DbScriptController {

    private final DbScriptService service;

    /** 分页查询 */
    @GetMapping("/page")
    public Result<PageResult<DbScriptVO>> page(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String sortField,
            @RequestParam(required = false) String sortOrder) {
        return Result.ok(service.page(pageNum, pageSize, keyword, sortField, sortOrder));
    }

    /** 新增 */
    @PostMapping
    public Result<DbScriptVO> create(@Valid @RequestBody DbScriptDTO dto) {
        return Result.ok("添加成功", service.create(dto));
    }

    /** 修改 */
    @PutMapping("/{id}")
    public Result<DbScriptVO> update(@PathVariable Long id, @Valid @RequestBody DbScriptDTO dto) {
        return Result.ok("修改成功", service.update(id, dto));
    }

    /** 删除 */
    @DeleteMapping("/{id}")
    public Result<String> delete(@PathVariable Long id) {
        service.delete(id);
        return Result.ok("删除成功");
    }

    /** 执行预览：在指定数据源上执行SQL并返回分页结果 */
    @PostMapping("/execute")
    public Result<PageResult<Map<String, Object>>> execute(
            @RequestParam Long scriptId,
            @RequestParam Long datasourceId,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        return Result.ok(service.executePreview(scriptId, datasourceId, pageNum, Math.min(pageSize, 200)));
    }

    /** 测试SQL：传入SQL内容和数据源ID直接执行（无需保存脚本） */
    @PostMapping(value = "/test", consumes = {"text/plain", "application/json"})
    public Result<PageResult<Map<String, Object>>> test(
            @RequestBody String sqlContent,
            @RequestParam Long datasourceId,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        return Result.ok(service.testSql(sqlContent, datasourceId, pageNum, Math.min(pageSize, 200)));
    }
}
