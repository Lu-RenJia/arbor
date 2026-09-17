package com.arbor.controller;

import com.arbor.common.PageResult;
import com.arbor.common.RequiresPermission;
import com.arbor.common.Audit;
import com.arbor.common.Result;
import com.arbor.dto.DepartmentDTO;
import com.arbor.service.DepartmentService;
import com.arbor.vo.DepartmentVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 部门管理控制器
 *
 * @author gwl
 */
@RestController
@RequestMapping("/api/department")
@RequiredArgsConstructor
public class DepartmentController {

    private final DepartmentService service;

    @GetMapping("/page")
    @RequiresPermission("department:list")
    public Result<PageResult<DepartmentVO>> page(@RequestParam(defaultValue = "1") int pageNum,
                                                  @RequestParam(defaultValue = "10") int pageSize) {
        return Result.ok(service.page(pageNum, pageSize));
    }

    @GetMapping("/list")
    @RequiresPermission("department:list")
    public Result<List<DepartmentVO>> list() {
        return Result.ok(service.listAll());
    }

    /** 部门树（下拉选择用） */
    @GetMapping("/treeselect")
    @RequiresPermission("department:list")
    public Result<List<DepartmentVO>> treeselect() {
        return Result.ok(service.treeselect());
    }

    @PostMapping
    @Audit(module = "部门", action = "CREATE")
    @RequiresPermission("department:create")
    public Result<DepartmentVO> create(@Valid @RequestBody DepartmentDTO dto) {
        return Result.ok("添加成功", service.create(dto));
    }

    @PutMapping("/{id}")
    @Audit(module = "部门", action = "UPDATE")
    @RequiresPermission("department:update")
    public Result<DepartmentVO> update(@PathVariable Long id, @Valid @RequestBody DepartmentDTO dto) {
        return Result.ok("修改成功", service.update(id, dto));
    }

    @DeleteMapping("/{id}")
    @Audit(module = "部门", action = "DELETE")
    @RequiresPermission("department:delete")
    public Result<String> delete(@PathVariable Long id) {
        service.delete(id);
        return Result.ok("删除成功");
    }
}
