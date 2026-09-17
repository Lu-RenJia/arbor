package com.arbor.controller;

import com.arbor.common.Audit;
import com.arbor.common.PageResult;
import com.arbor.common.Result;
import com.arbor.dto.RedisDatasourceDTO;
import com.arbor.service.RedisDatasourceService;
import com.arbor.vo.RedisDatasourceVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/redis-datasource")
@RequiredArgsConstructor
public class RedisDatasourceController {
    private final RedisDatasourceService service;

    @GetMapping("/page")
    public Result<PageResult<RedisDatasourceVO>> page(@RequestParam(defaultValue = "1") int pageNum,
                                                       @RequestParam(defaultValue = "10") int pageSize,
                                                       @RequestParam(required = false) String keyword,
                                                       @RequestParam(required = false) String sortField,
                                                       @RequestParam(required = false) String sortOrder) {
        return Result.ok(service.page(pageNum, pageSize, keyword, sortField, sortOrder));
    }

    @Audit(module = "Redis数据源", action = "CREATE")
    @PostMapping
    public Result<RedisDatasourceVO> create(@Valid @RequestBody RedisDatasourceDTO dto) {
        return Result.ok("添加成功", service.create(dto));
    }

    @Audit(module = "Redis数据源", action = "UPDATE")
    @PutMapping("/{id}")
    public Result<RedisDatasourceVO> update(@PathVariable Long id, @Valid @RequestBody RedisDatasourceDTO dto) {
        return Result.ok("修改成功", service.update(id, dto));
    }

    @Audit(module = "Redis数据源", action = "DELETE")
    @DeleteMapping("/{id}")
    public Result<String> delete(@PathVariable Long id) { service.delete(id); return Result.ok("删除成功"); }

    @PostMapping("/test/{id}")
    public Result<String> test(@PathVariable Long id) {
        return service.testConnection(id) ? Result.ok("连接成功") : Result.fail("连接失败");
    }

    @PostMapping("/test")
    public Result<String> testRaw(@Valid @RequestBody RedisDatasourceDTO dto) {
        return service.testConnectionRaw(dto) ? Result.ok("连接成功") : Result.fail("连接失败");
    }
}
