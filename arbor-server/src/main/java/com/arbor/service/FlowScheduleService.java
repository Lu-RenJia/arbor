package com.arbor.service;

import com.arbor.common.AssertUtils;
import com.arbor.common.BusinessException;
import com.arbor.common.ErrorCode;
import com.arbor.common.TenantContext;
import com.arbor.dto.FlowScheduleDTO;
import com.arbor.entity.FlowSchedule;
import com.arbor.mapper.FlowMapper;
import com.arbor.mapper.FlowScheduleMapper;
import com.arbor.schedule.ScheduleManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 流程调度服务（仅 admin 可配置/启停）
 *
 * @author gwl
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlowScheduleService {

    private final FlowScheduleMapper scheduleMapper;
    private final FlowMapper flowMapper;
    private final ScheduleManager scheduleManager;

    /** 获取调度配置（所有用户可读，用于展示） */
    public FlowSchedule getByFlowId(Long flowId) {
        return scheduleMapper.selectByFlowId(flowId);
    }

    /** 保存/修改调度配置（仅 admin） */
    @Transactional
    public void save(Long flowId, FlowScheduleDTO dto) {
        checkAdmin();
        var flow = flowMapper.selectById(flowId);
        AssertUtils.notNull(flow, ErrorCode.FLOW_NOT_FOUND);

        // 校验 cron 合法性（Spring 6位格式）
        try {
            CronExpression.parse(dto.getCronExpr());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.SCHEDULE_CRON_INVALID, e.getMessage());
        }

        FlowSchedule exist = scheduleMapper.selectByFlowId(flowId);
        LocalDateTime nextTime = ScheduleManager.computeNextTime(dto.getCronExpr());

        if (exist == null) {
            FlowSchedule schedule = new FlowSchedule();
            schedule.setFlowId(flowId);
            schedule.setCronExpr(dto.getCronExpr());
            schedule.setEnabled(0);
            schedule.setMaxExecCount(dto.getMaxExecCount());
            schedule.setExecCount(0);
            scheduleMapper.insert(schedule);
        } else {
            exist.setCronExpr(dto.getCronExpr());
            exist.setMaxExecCount(dto.getMaxExecCount());
            exist.setNextExecTime(exist.getEnabled() == 1 ? nextTime : null);
            scheduleMapper.updateByFlowId(exist);
            // 已启用的任务重新注册
            if (exist.getEnabled() == 1) {
                scheduleManager.register(flowId, dto.getCronExpr());
            }
        }
        log.info("保存流程调度配置: flowId={}, cron={}", flowId, dto.getCronExpr());
    }

    /** 启用/停用（仅 admin） */
    @Transactional
    public void toggle(Long flowId, boolean enabled) {
        checkAdmin();
        FlowSchedule schedule = scheduleMapper.selectByFlowId(flowId);
        AssertUtils.notNull(schedule, ErrorCode.SCHEDULE_NOT_FOUND);

        if (enabled) {
            // 次数用尽不允许再启用
            if (schedule.getMaxExecCount() != null && schedule.getExecCount() >= schedule.getMaxExecCount()) {
                throw new BusinessException(ErrorCode.SCHEDULE_EXEC_LIMIT_REACHED);
            }
            LocalDateTime next = ScheduleManager.computeNextTime(schedule.getCronExpr());
            scheduleMapper.updateEnabled(flowId, 1, next);
            scheduleManager.register(flowId, schedule.getCronExpr());
        } else {
            scheduleMapper.updateEnabled(flowId, 0, null);
            scheduleManager.unregister(flowId);
        }
        log.info("定时任务{}: flowId={}", enabled ? "启用" : "停用", flowId);
    }

    /** 计算最近 N 次执行时间 */
    public List<LocalDateTime> nextTimes(String cron, int count) {
        CronExpression expression;
        try {
            expression = CronExpression.parse(cron);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.SCHEDULE_CRON_INVALID, cron);
        }
        List<LocalDateTime> times = new ArrayList<>();
        LocalDateTime base = LocalDateTime.now();
        for (int i = 0; i < count; i++) {
            LocalDateTime next = expression.next(base);
            if (next == null) break;
            times.add(next);
            base = next;
        }
        return times;
    }

    /** 删除调度配置（流程删除时联动调用） */
    public void deleteByFlowId(Long flowId) {
        scheduleManager.unregister(flowId);
        scheduleMapper.deleteByFlowId(flowId);
    }

    private void checkAdmin() {
        if (!TenantContext.isAdmin()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅管理员可配置定时任务");
        }
    }
}
