package com.arbor.controller;

import com.arbor.common.PageResult;
import com.arbor.common.RequiresPermission;
import com.arbor.common.Result;
import com.arbor.entity.AuditLog;
import com.arbor.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 操作审计日志控制器（管理员专用）
 *
 * @author gwl
 */
@RestController
@RequestMapping("/api/audit-log")
@RequiredArgsConstructor
public class AuditLogController {

    private final AuditLogService auditLogService;

    /** 分页查询审计日志 */
    @GetMapping("/page")
    @RequiresPermission("audit:list")
    public Result<PageResult<AuditLog>> page(@RequestParam(defaultValue = "1") int pageNum,
                                             @RequestParam(defaultValue = "20") int pageSize,
                                             @RequestParam(required = false) String moduleName,
                                             @RequestParam(required = false) String username,
                                             @RequestParam(required = false) String keyword) {
        return Result.ok(auditLogService.page(pageNum, pageSize, moduleName, username, keyword));
    }
}
