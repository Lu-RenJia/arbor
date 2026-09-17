package com.arbor.controller;

import com.arbor.common.Result;
import com.arbor.dto.FlowScheduleDTO;
import com.arbor.entity.FlowSchedule;
import com.arbor.service.FlowScheduleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 流程定时调度控制器
 * <p>
 * 写操作仅 admin 可用（Service 层强制校验）
 *
 * @author gwl
 */
@RestController
@RequestMapping("/api/flow-schedule")
@RequiredArgsConstructor
public class FlowScheduleController {

    private final FlowScheduleService service;

    /** 获取调度配置（所有用户可读） */
    @GetMapping("/{flowId}")
    public Result<FlowSchedule> get(@PathVariable Long flowId) {
        return Result.ok(service.getByFlowId(flowId));
    }

    /** 保存调度配置（仅 admin） */
    @PutMapping("/{flowId}")
    public Result<String> save(@PathVariable Long flowId, @Valid @RequestBody FlowScheduleDTO dto) {
        service.save(flowId, dto);
        return Result.ok("保存成功");
    }

    /** 启用/停用（仅 admin） */
    @PutMapping("/toggle/{flowId}")
    public Result<String> toggle(@PathVariable Long flowId, @RequestParam boolean enabled) {
        service.toggle(flowId, enabled);
        return Result.ok(enabled ? "已启用" : "已停用");
    }

    /** 计算最近 N 次执行时间（供前端预览） */
    @GetMapping("/next-times")
    public Result<List<LocalDateTime>> nextTimes(@RequestParam String cron,
                                                 @RequestParam(defaultValue = "10") int count) {
        return Result.ok(service.nextTimes(cron, count));
    }
}
