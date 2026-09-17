package com.arbor.schedule;

import com.arbor.entity.FlowSchedule;
import com.arbor.mapper.FlowScheduleMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 应用启动时从 DB 恢复所有已启用的调度任务
 *
 * @author gwl
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScheduleBootstrapper implements ApplicationRunner {

    private final FlowScheduleMapper scheduleMapper;
    private final ScheduleManager scheduleManager;

    @Override
    public void run(ApplicationArguments args) {
        List<FlowSchedule> enabledList = scheduleMapper.selectAllEnabled();
        for (FlowSchedule schedule : enabledList) {
            try {
                scheduleManager.register(schedule.getFlowId(), schedule.getCronExpr());
            } catch (Exception e) {
                log.error("恢复定时任务失败: flowId={}, cron={}", schedule.getFlowId(), schedule.getCronExpr(), e);
            }
        }
        log.info("定时任务恢复完成，共注册 {} 个", enabledList.size());
    }
}
