package com.arbor.controller;

import com.arbor.common.PageResult;
import com.arbor.common.Result;
import com.arbor.dto.ProgramScriptDTO;
import com.arbor.service.ProgramScriptService;
import com.arbor.vo.ProgramScriptVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 程序脚本控制器
 *
 * @author gwl
 */
@Slf4j
@RestController
@RequestMapping("/api/program-script")
@RequiredArgsConstructor
public class ProgramScriptController {

    private final ProgramScriptService service;

    /** 分页查询 */
    @GetMapping("/page")
    public Result<PageResult<ProgramScriptVO>> page(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String sortField,
            @RequestParam(required = false) String sortOrder) {
        return Result.ok(service.page(pageNum, pageSize, keyword, sortField, sortOrder));
    }

    /** 新增（自动生成scriptId） */
    @PostMapping
    public Result<ProgramScriptVO> create(@Valid @RequestBody ProgramScriptDTO dto) {
        return Result.ok("添加成功", service.create(dto));
    }

    /** 修改 */
    @PutMapping("/{id}")
    public Result<ProgramScriptVO> update(@PathVariable Long id, @Valid @RequestBody ProgramScriptDTO dto) {
        return Result.ok("修改成功", service.update(id, dto));
    }

    /** 删除 */
    @DeleteMapping("/{id}")
    public Result<String> delete(@PathVariable Long id) {
        service.delete(id);
        return Result.ok("删除成功");
    }

    /** 运行已保存的脚本：注入全局参数后执行并返回结果 */
    @PostMapping("/run/{scriptId}")
    public Result<Object> run(@PathVariable String scriptId) {
        Object result = service.runScript(scriptId);
        return Result.ok(result);
    }

    /** 测试脚本：传入脚本内容直接执行（无需保存），返回结果 */
    @PostMapping(value = "/test", consumes = {"text/plain", "application/json"})
    public Result<Object> test(@RequestBody String scriptContent) {
        Object result = service.testScript(scriptContent);
        return Result.ok(result);
    }
}
