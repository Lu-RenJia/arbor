package com.arbor.controller;

import com.arbor.common.PageResult;
import com.arbor.common.Result;
import com.arbor.dto.RedisScriptDTO;
import com.arbor.service.RedisScriptService;
import com.arbor.vo.RedisScriptVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/redis-script")
@RequiredArgsConstructor
public class RedisScriptController {
    private final RedisScriptService service;

    @GetMapping("/page")
    public Result<PageResult<RedisScriptVO>> page(@RequestParam(defaultValue = "1") int pageNum,
                                                   @RequestParam(defaultValue = "10") int pageSize,
                                                   @RequestParam(required = false) String keyword,
                                                   @RequestParam(required = false) String sortField,
                                                   @RequestParam(required = false) String sortOrder) {
        return Result.ok(service.page(pageNum, pageSize, keyword, sortField, sortOrder));
    }
    @PostMapping public Result<RedisScriptVO> create(@Valid @RequestBody RedisScriptDTO dto) { return Result.ok("添加成功", service.create(dto)); }
    @PutMapping("/{id}") public Result<RedisScriptVO> update(@PathVariable Long id, @Valid @RequestBody RedisScriptDTO dto) { return Result.ok("修改成功", service.update(id, dto)); }
    @DeleteMapping("/{id}") public Result<String> delete(@PathVariable Long id) { service.delete(id); return Result.ok("删除成功"); }
    @PostMapping("/test/{id}") public Result<Object> test(@PathVariable Long id) { return Result.ok(service.testExecute(id)); }
    /** 草稿测试：不落库，直接按表单内容执行（GET&SET 只执行 GET） */
    @PostMapping("/test") public Result<Object> testDraft(@Valid @RequestBody RedisScriptDTO dto) { return Result.ok(service.testExecuteDraft(dto)); }
}
