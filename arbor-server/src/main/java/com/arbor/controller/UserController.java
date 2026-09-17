package com.arbor.controller;

import com.arbor.common.Audit;
import com.arbor.common.PageResult;
import com.arbor.common.RequiresPermission;
import com.arbor.common.Result;
import com.arbor.dto.UserDTO;
import com.arbor.service.UserService;
import com.arbor.vo.UserVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 用户管理控制器（管理员专用）
 *
 * @author gwl
 */
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService service;

    @GetMapping("/page")
    @RequiresPermission("user:list")
    public Result<PageResult<UserVO>> page(@RequestParam(defaultValue = "1") int pageNum,
                                            @RequestParam(defaultValue = "10") int pageSize) {
        return Result.ok(service.page(pageNum, pageSize));
    }

    @GetMapping("/list")
    @RequiresPermission("user:list")
    public Result<List<UserVO>> list() {
        return Result.ok(service.listAll());
    }

    @PostMapping
    @Audit(module = "用户", action = "CREATE")
    @RequiresPermission("user:create")
    public Result<UserVO> create(@Valid @RequestBody UserDTO dto) {
        return Result.ok("添加成功", service.create(dto));
    }

    @PutMapping("/{id}")
    @Audit(module = "用户", action = "UPDATE")
    @RequiresPermission("user:update")
    public Result<UserVO> update(@PathVariable Long id, @Valid @RequestBody UserDTO dto) {
        return Result.ok("修改成功", service.update(id, dto));
    }

    /** 状态切换 */
    @PutMapping("/status/{id}")
    @Audit(module = "用户", action = "UPDATE", description = "启用/禁用")
    @RequiresPermission("user:update")
    public Result<String> changeStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        service.updateStatus(id, body.get("status"));
        return Result.ok("状态修改成功");
    }

    @PutMapping("/reset-password/{id}")
    @Audit(module = "用户", action = "RESET_PASSWORD", description = "重置密码")
    @RequiresPermission("user:reset-password")
    public Result<String> resetPassword(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String pwd = body.get("password");
        if (pwd == null || pwd.isBlank()) return Result.fail("密码不能为空");
        service.resetPassword(id, pwd);
        return Result.ok("密码重置成功");
    }

    @DeleteMapping("/{id}")
    @Audit(module = "用户", action = "DELETE")
    @RequiresPermission("user:delete")
    public Result<String> delete(@PathVariable Long id) {
        service.delete(id);
        return Result.ok("删除成功");
    }

    /** 查询用户已分配的角色ID */
    @GetMapping("/authRole/{userId}")
    @RequiresPermission("user:update")
    public Result<List<Long>> getAuthRole(@PathVariable Long userId) {
        return Result.ok(service.getAuthRoleIds(userId));
    }

    /** 分配用户角色 */
    @PutMapping("/authRole")
    @Audit(module = "用户", action = "UPDATE", description = "分配角色")
    @RequiresPermission("user:update")
    public Result<String> authRole(@RequestBody Map<String, Object> body) {
        Long userId = Long.valueOf(body.get("userId").toString());
        @SuppressWarnings("unchecked")
        List<Integer> roleIdsRaw = (List<Integer>) body.get("roleIds");
        List<Long> roleIds = roleIdsRaw.stream().map(Long::valueOf).toList();
        service.assignUserRoles(userId, roleIds);
        return Result.ok("角色分配成功");
    }
}
