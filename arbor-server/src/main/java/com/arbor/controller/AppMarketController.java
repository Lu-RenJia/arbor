package com.arbor.controller;

import com.arbor.common.PageResult;
import com.arbor.common.RequiresPermission;
import com.arbor.common.Result;
import com.arbor.entity.AppMarket;
import com.arbor.service.AppMarketService;
import com.arbor.vo.AppMarketAppVO;
import com.arbor.vo.AppMarketVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/app-market")
@RequiredArgsConstructor
public class AppMarketController {

    private final AppMarketService service;

    @GetMapping("/page")
    @RequiresPermission("app:market:list")
    public Result<PageResult<AppMarketVO>> page(@RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String name) {
        return Result.ok(service.page(pageNum, pageSize, name));
    }

    @PostMapping
    @RequiresPermission("app:market:create")
    public Result<AppMarketVO> create(@RequestBody Map<String, String> body) {
        return Result.ok("创建成功", service.create(body.get("name"), body.get("remark")));
    }

    @PutMapping("/{id}")
    @RequiresPermission("app:market:update")
    public Result<AppMarketVO> update(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return Result.ok("修改成功", service.update(id, body.get("name"), body.get("remark")));
    }

    @DeleteMapping("/{id}")
    @RequiresPermission("app:market:delete")
    public Result<String> delete(@PathVariable Long id) {
        service.delete(id);
        return Result.ok("删除成功");
    }

    @GetMapping("/my")
    @RequiresPermission("app:market:list")
    public Result<List<AppMarket>> myMarkets() {
        return Result.ok(service.listUserMarkets());
    }

    @GetMapping("/users/{id}")
    @RequiresPermission("app:market:list")
    public Result<List<Long>> getUsers(@PathVariable Long id) {
        return Result.ok(service.getUsers(id));
    }

    @PutMapping("/users/{id}")
    @RequiresPermission("app:market:update")
    public Result<String> assignUsers(@PathVariable Long id, @RequestBody List<Long> userIds) {
        service.assignUsers(id, userIds);
        return Result.ok("人员分配成功");
    }

    @GetMapping("/apps/{id}")
    @RequiresPermission("app:market:list")
    public Result<List<AppMarketAppVO>> getApps(@PathVariable Long id,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String sharedBy) {
        return Result.ok(service.getMarketApps(id, name, sharedBy));
    }
}
