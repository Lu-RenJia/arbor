package com.arbor.controller;

import com.arbor.common.Audit;
import com.arbor.common.PageResult;
import com.arbor.common.RequiresPermission;
import com.arbor.common.Result;
import com.arbor.dto.RoleDTO;
import com.arbor.service.RoleService;
import com.arbor.vo.RoleVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 角色管理控制器
 *
 * @author gwl
 */
@RestController
@RequestMapping("/api/role")
@RequiredArgsConstructor
public class RoleController {

    private final RoleService service;

    @GetMapping("/page")
    @RequiresPermission("role:list")
    public Result<PageResult<RoleVO>> page(@RequestParam(defaultValue = "1") int pageNum,
                                           @RequestParam(defaultValue = "10") int pageSize) {
        return Result.ok(service.page(pageNum, pageSize));
    }

    @GetMapping("/list")
    @RequiresPermission("role:list")
    public Result<List<RoleVO>> list() {
        return Result.ok(service.listAll());
    }

    @GetMapping("/{id}")
    @RequiresPermission("role:list")
    public Result<RoleVO> detail(@PathVariable Long id) {
        return Result.ok(service.getDetail(id));
    }

    @PostMapping
    @Audit(module = "角色", action = "CREATE")
    @RequiresPermission("role:create")
    public Result<RoleVO> create(@Valid @RequestBody RoleDTO dto) {
        return Result.ok("添加成功", service.create(dto));
    }

    @PutMapping("/{id}")
    @Audit(module = "角色", action = "UPDATE")
    @RequiresPermission("role:update")
    public Result<RoleVO> update(@PathVariable Long id, @Valid @RequestBody RoleDTO dto) {
        return Result.ok("修改成功", service.update(id, dto));
    }

    @DeleteMapping("/{id}")
    @Audit(module = "角色", action = "DELETE")
    @RequiresPermission("role:delete")
    public Result<String> delete(@PathVariable Long id) {
        service.delete(id);
        return Result.ok("删除成功");
    }

    /** 分配权限 */
    @PostMapping("/permissions/{id}")
    @Audit(module = "角色", action = "UPDATE", description = "分配权限")
    @RequiresPermission("role:update")
    public Result<String> assignPermissions(@PathVariable Long id, @RequestBody List<Long> permissionIds) {
        service.assignPermissions(id, permissionIds);
        return Result.ok("权限分配成功");
    }

    /** 分配数据权限范围 */
    @PutMapping("/dataScope/{id}")
    @Audit(module = "角色", action = "UPDATE", description = "分配数据权限")
    @RequiresPermission("role:update")
    public Result<String> saveDataScope(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        service.saveDataScope(id, body.get("dataScope") != null ? body.get("dataScope").toString() : "1");
        return Result.ok("数据权限分配成功");
    }
}
