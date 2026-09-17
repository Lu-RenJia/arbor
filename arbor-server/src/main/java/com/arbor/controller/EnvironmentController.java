package com.arbor.controller;

import com.arbor.common.PageResult;
import com.arbor.common.Audit;
import com.arbor.common.Result;
import com.arbor.dto.EnvironmentDTO;
import com.arbor.service.EnvironmentService;
import com.arbor.vo.EnvironmentVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 环境配置控制器
 *
 * @author gwl
 */
@Slf4j
@RestController
@RequestMapping("/api/environment")
@RequiredArgsConstructor
public class EnvironmentController {

    private final EnvironmentService service;

    /** 分页查询 */
    @GetMapping("/page")
    public Result<PageResult<EnvironmentVO>> page(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String sortField,
            @RequestParam(required = false) String sortOrder) {
        return Result.ok(service.page(pageNum, pageSize, keyword, sortField, sortOrder));
    }

    /** 活跃环境列表（给应用选择等场景用） */
    @GetMapping("/active")
    public Result<List<EnvironmentVO>> active() {
        return Result.ok(service.listActive());
    }

    /** 获取默认环境ID */
    @GetMapping("/default")
    public Result<Long> getDefault() {
        return Result.ok(service.getDefaultEnvId());
    }

    /** 新增 */
    @Audit(module = "环境", action = "CREATE")
    @PostMapping
    public Result<EnvironmentVO> create(@Valid @RequestBody EnvironmentDTO dto) {
        return Result.ok("添加成功", service.create(dto));
    }

    /** 修改 */
    @Audit(module = "环境", action = "UPDATE")
    @PutMapping("/{id}")
    public Result<EnvironmentVO> update(@PathVariable Long id, @Valid @RequestBody EnvironmentDTO dto) {
        return Result.ok("修改成功", service.update(id, dto));
    }

    /** 删除（软删除） */
    @Audit(module = "环境", action = "DELETE")
    @DeleteMapping("/{id}")
    public Result<String> delete(@PathVariable Long id) {
        service.delete(id);
        return Result.ok("删除成功");
    }
}
