package com.arbor.controller;

import com.arbor.common.Audit;
import com.arbor.common.RequiresPermission;
import com.arbor.common.Result;
import com.arbor.dto.MenuDTO;
import com.arbor.service.MenuService;
import com.arbor.vo.MenuVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 菜单管理控制器
 *
 * @author gwl
 */
@RestController
@RequestMapping("/api/menu")
@RequiredArgsConstructor
public class MenuController {

    private final MenuService service;

    /** 菜单树（管理端全部） */
    @GetMapping("/tree")
    @RequiresPermission("menu:list")
    public Result<List<MenuVO>> tree() {
        return Result.ok(service.getMenuTree());
    }

    /** 菜单树选择（下拉用，不含按钮） */
    @GetMapping("/treeselect")
    @RequiresPermission("menu:list")
    public Result<List<MenuVO>> treeselect() {
        return Result.ok(service.treeselect());
    }

    /** 当前用户可见菜单树 */
    @GetMapping("/my")
    public Result<List<MenuVO>> myMenus() {
        var auth = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication();
        if (auth == null) return Result.ok(List.of());
        String roleCode = auth.getAuthorities().stream()
                .map(Object::toString)
                .filter(a -> a.startsWith("ROLE_"))
                .findFirst()
                .map(a -> a.substring(5))
                .orElse("USER");
        return Result.ok(service.getMenuTreeByRoleCode(roleCode));
    }

    /** 扁平列表 */
    @GetMapping("/list")
    @RequiresPermission("menu:list")
    public Result<List<MenuVO>> list() {
        return Result.ok(service.listAll());
    }

    /** 详情 */
    @GetMapping("/{id}")
    @RequiresPermission("menu:list")
    public Result<MenuVO> detail(@PathVariable Long id) {
        return Result.ok(service.getById(id));
    }

    /** 新增 */
    @PostMapping
    @Audit(module = "菜单", action = "CREATE")
    @RequiresPermission("menu:create")
    public Result<MenuVO> create(@RequestBody MenuDTO dto) {
        return Result.ok("添加成功", service.create(dto));
    }

    /** 修改 */
    @PutMapping("/{id}")
    @Audit(module = "菜单", action = "UPDATE")
    @RequiresPermission("menu:update")
    public Result<MenuVO> update(@PathVariable Long id, @RequestBody MenuDTO dto) {
        return Result.ok("修改成功", service.update(id, dto));
    }

    /** 删除 */
    @DeleteMapping("/{id}")
    @Audit(module = "菜单", action = "DELETE")
    @RequiresPermission("menu:delete")
    public Result<String> delete(@PathVariable Long id) {
        service.delete(id);
        return Result.ok("删除成功");
    }
}
