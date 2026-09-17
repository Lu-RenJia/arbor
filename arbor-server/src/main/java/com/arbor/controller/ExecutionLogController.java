package com.arbor.controller;

import com.arbor.common.PageResult;
import com.arbor.common.Result;
import com.arbor.service.ExecutionLogService;
import com.arbor.vo.ExecutionLogVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

/**
 * 执行日志控制器
 *
 * @author gwl
 */
@Slf4j
@RestController
@RequestMapping("/api/execution-log")
@RequiredArgsConstructor
public class ExecutionLogController {

    private final ExecutionLogService service;

    /** 分页查询（支持多条件过滤） */
    @GetMapping("/page")
    public Result<PageResult<ExecutionLogVO>> page(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String flowName,
            @RequestParam(required = false) String executeType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) Long ownerId,
            @RequestParam(required = false) Long executorId,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {
        return Result.ok(service.page(pageNum, pageSize, flowName, executeType, status, source, ownerId, executorId, startTime, endTime));
    }

    /** 执行详情（含节点日志） */
    @GetMapping("/{id}")
    public Result<ExecutionLogVO> detail(@PathVariable Long id) {
        return Result.ok(service.getDetail(id));
    }
}
