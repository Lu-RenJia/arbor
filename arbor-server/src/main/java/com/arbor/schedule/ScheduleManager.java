package com.arbor.schedule;

import com.arbor.common.EnvironmentContext;
import com.arbor.common.TenantContext;
import com.arbor.engine.FlowEngine;
import com.arbor.entity.Environment;
import com.arbor.entity.FlowSchedule;
import com.arbor.mapper.FlowScheduleMapper;
import com.arbor.mapper.UserMapper;
import com.arbor.service.EnvironmentService;
import com.arbor.util.RedisLockUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

/**
 * 流程调度管理器：动态注册/取消定时任务 + 触发执行
 * <p>
 * 调度器为纯内存态，DB（flow_schedule 表）为唯一配置源；
 * 应用启动时由 ScheduleBootstrapper 全量重载。
 * 多实例部署时触发执行用 Redis 分布式锁互斥抢占，同一时刻只有一个实例执行同一流程。
 *
 * @author gwl
 */
@Slf4j
@Component
public class ScheduleManager {

    private final FlowEngine flowEngine;
    private final FlowScheduleMapper scheduleMapper;
    private final EnvironmentService environmentService;
    private final UserMapper userMapper;
    private final RedisLockUtil redisLockUtil;

    /** 定时任务互斥锁 key 前缀 */
    private static final String LOCK_KEY_PREFIX = "flow_schedule:lock:";
    /** 锁有效期：覆盖最长流程执行时间（脚本超时上限 300s + 其他节点冗余），持锁实例崩溃后自动释放 */
    private static final Duration LOCK_TTL = Duration.ofMinutes(30);

    /** 已注册的调度任务 */
    private final Map<Long, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();

    /** 防堆积：正在执行中的流程 */
    private final Map<Long, Boolean> runningMap = new ConcurrentHashMap<>();

    /** 缓存 admin 用户ID */
    private volatile Long adminUserId;

    /** 内部调度线程池（非 Spring Bean，避免循环依赖） */
    private final ThreadPoolTaskScheduler taskScheduler = createScheduler();

    public ScheduleManager(FlowEngine flowEngine, FlowScheduleMapper scheduleMapper,
                           EnvironmentService environmentService, UserMapper userMapper,
                           RedisLockUtil redisLockUtil) {
        this.flowEngine = flowEngine;
        this.scheduleMapper = scheduleMapper;
        this.environmentService = environmentService;
        this.userMapper = userMapper;
        this.redisLockUtil = redisLockUtil;
    }

    private static ThreadPoolTaskScheduler createScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(4);
        scheduler.setThreadNamePrefix("flow-schedule-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(30);
        scheduler.initialize();
        return scheduler;
    }

    /**
     * 注册流程调度（Cron 表达式）
     */
    public void register(Long flowId, String cronExpr) {
        unregister(flowId);
        ScheduledFuture<?> future = taskScheduler.schedule(
                () -> executeScheduled(flowId),
                new CronTrigger(cronExpr)
        );
        scheduledTasks.put(flowId, future);
        log.info("注册定时任务: flowId={}, cron={}", flowId, cronExpr);
    }

    /**
     * 取消流程调度
     */
    public void unregister(Long flowId) {
        ScheduledFuture<?> future = scheduledTasks.remove(flowId);
        if (future != null) {
            future.cancel(false);
            log.info("取消定时任务: flowId={}", flowId);
        }
    }

    /**
     * 判断是否已注册
     */
    public boolean isRegistered(Long flowId) {
        return scheduledTasks.containsKey(flowId);
    }

    /**
     * 定时触发执行（调度线程内调用）
     */
    private void executeScheduled(Long flowId) {
        // 防堆积：上次未完成则跳过（进程内兜底）
        if (runningMap.putIfAbsent(flowId, Boolean.TRUE) != null) {
            log.warn("定时任务跳过（上次未完成）: flowId={}", flowId);
            return;
        }
        // 分布式互斥：多实例部署时只有一个实例能抢到锁（Redis 不可用时降级单机模式）
        String lockKey = LOCK_KEY_PREFIX + flowId;
        String lockValue = RedisLockUtil.newLockValue();
        if (!redisLockUtil.tryLock(lockKey, lockValue, LOCK_TTL)) {
            log.info("定时任务跳过（其他实例正在执行）: flowId={}", flowId);
            runningMap.remove(flowId);
            return;
        }
        try {
            // admin 身份执行，跳过租户过滤
            TenantContext.set(resolveAdminId(), true);
            // 环境上下文：系统默认环境
            Long envId = environmentService.getDefaultEnvId();
            if (envId != null) {
                Environment env = environmentService.getByIdCached(envId);
                if (env != null) {
                    EnvironmentContext.set(envId, env.getCode());
                }
            }

            log.info("定时任务触发: flowId={}", flowId);
            flowEngine.execute(flowId, FlowEngine.EXECUTE_AUTO, FlowEngine.SOURCE_FLOW_AUTO);

            // 更新执行统计 + 下次执行时间
            FlowSchedule schedule = scheduleMapper.selectByFlowId(flowId);
            if (schedule != null) {
                LocalDateTime next = computeNextTime(schedule.getCronExpr());
                scheduleMapper.incrExecCount(flowId, LocalDateTime.now(), next);
                // 次数用尽 → 自动停用
                int newCount = (schedule.getExecCount() != null ? schedule.getExecCount() : 0) + 1;
                if (schedule.getMaxExecCount() != null && newCount >= schedule.getMaxExecCount()) {
                    scheduleMapper.disableByFlowId(flowId);
                    unregister(flowId);
                    log.info("定时任务次数用尽，自动停用: flowId={}, count={}", flowId, newCount);
                }
            }
        } catch (Exception e) {
            log.error("定时任务执行失败: flowId={}", flowId, e);
        } finally {
            redisLockUtil.unlock(lockKey, lockValue);
            EnvironmentContext.clear();
            TenantContext.clear();
            runningMap.remove(flowId);
        }
    }

    /**
     * 计算 cron 表达式的下次执行时间
     */
    public static LocalDateTime computeNextTime(String cronExpr) {
        try {
            CronExpression cron = CronExpression.parse(cronExpr);
            return cron.next(LocalDateTime.now());
        } catch (Exception e) {
            return null;
        }
    }

    private Long resolveAdminId() {
        if (adminUserId == null) {
            synchronized (this) {
                if (adminUserId == null) {
                    var admin = userMapper.selectByUsername("admin");
                    adminUserId = admin != null ? admin.getId() : 1L;
                }
            }
        }
        return adminUserId;
    }
}
