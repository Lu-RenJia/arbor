package com.arbor.controller;

import com.arbor.common.AssertUtils;
import com.arbor.common.ErrorCode;
import com.arbor.common.PageResult;
import com.arbor.common.RequiresPermission;
import com.arbor.common.Result;
import com.arbor.dto.ApplicationDTO;
import com.arbor.service.ApplicationService;
import com.arbor.vo.ApplicationVO;
import com.arbor.vo.AppShareVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/application")
@RequiredArgsConstructor
public class ApplicationController {

    private final ApplicationService service;

    @GetMapping("/page")
    public Result<PageResult<ApplicationVO>> page(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String flowName) {
        return Result.ok(service.page(pageNum, pageSize, name, flowName));
    }

    @PostMapping
    public Result<ApplicationVO> create(@Valid @RequestBody ApplicationDTO dto) {
        return Result.ok("添加成功", service.create(dto));
    }

    @PutMapping("/{id}")
    public Result<ApplicationVO> update(@PathVariable Long id, @Valid @RequestBody ApplicationDTO dto) {
        return Result.ok("修改成功", service.update(id, dto));
    }

    @DeleteMapping("/{id}")
    public Result<String> delete(@PathVariable Long id) {
        service.delete(id);
        return Result.ok("删除成功");
    }

    @PostMapping("/execute/{id}")
    public Result<Object> execute(@PathVariable Long id,
                                   @RequestParam(required = false) Long marketId,
                                   @RequestParam(required = false) Long envId,
                                   @RequestParam(required = false) MultipartFile file) {
        return Result.ok(service.execute(id, marketId, envId, file));
    }

    /** 共享应用到广场（指定多个环境） */
    @PostMapping("/share/{id}")
    @RequiresPermission("app:my:list")
    public Result<String> share(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Long marketId = body.get("marketId") != null ? ((Number) body.get("marketId")).longValue() : null;
        @SuppressWarnings("unchecked")
        List<Integer> envIdsRaw = (List<Integer>) body.get("envIds");
        AssertUtils.notNull(marketId, ErrorCode.PARAM_NOT_FOUND, "marketId");
        AssertUtils.isTrue(envIdsRaw != null && !envIdsRaw.isEmpty(), ErrorCode.PARAM_NOT_FOUND, "envIds");
        List<Long> envIds = envIdsRaw.stream().map(Integer::longValue).toList();
        service.shareToMarket(id, marketId, envIds);
        return Result.ok("共享成功");
    }

    /** 查询某应用已共享到哪些广场（我的应用页展示与移除用） */
    @GetMapping("/share/{id}")
    @RequiresPermission("app:my:list")
    public Result<List<AppShareVO>> sharedMarkets(@PathVariable Long id) {
        return Result.ok(service.getSharedMarkets(id));
    }

    /** 取消共享 */
    @DeleteMapping("/share/{id}/{marketId}")
    @RequiresPermission("app:my:list")
    public Result<String> unshare(@PathVariable Long id, @PathVariable Long marketId) {
        service.unshareFromMarket(id, marketId);
        return Result.ok("已取消共享");
    }
}
