package com.arbor.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.arbor.common.AssertUtils;
import com.arbor.common.ErrorCode;
import com.arbor.common.PageResult;
import com.arbor.common.TenantContext;
import com.arbor.entity.ExecutionLog;
import com.arbor.entity.ExecutionNodeLog;
import com.arbor.mapper.ExecutionLogMapper;
import com.arbor.mapper.ExecutionNodeLogMapper;
import com.arbor.vo.ExecutionLogVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 执行日志服务
 *
 * @author gwl
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExecutionLogService {

    private final ExecutionLogMapper executionLogMapper;
    private final ExecutionNodeLogMapper executionNodeLogMapper;
    private final ExecutionScriptLogService executionScriptLogService;

    /** 分页查询执行日志（支持多条件过滤；管理员看全部，普通用户只看自己） */
    public PageResult<ExecutionLogVO> page(int pageNum, int pageSize,
                                           String flowName, String executeType, String status,
                                           String source, Long ownerId, Long executorId,
                                           LocalDateTime startTime, LocalDateTime endTime) {
        IPage<ExecutionLog> page = new Page<>(pageNum, pageSize);
        IPage<ExecutionLog> result = executionLogMapper.selectPage(page,
                flowName, executeType, status, source, ownerId, executorId, startTime, endTime,
                visibilityFilterUserId());
        List<ExecutionLogVO> voList = result.getRecords().stream().map(ExecutionLogVO::fromEntity).collect(Collectors.toList());
        return PageResult.of(pageNum, pageSize, result.getTotal(), voList);
    }

    /**
     * 列表可见性过滤值 —— {@code null} 仅表示「管理员，不按 user_id 过滤」，
     * 非 null 表示「只看该 user_id 的日志」。
     * <p>
     * 普通用户拿不到 userId 时必须抛未登录，绝不能返回 null：XML 里 filterUserId 为 null 会整条
     * 跳过 user_id 条件，等于把全库所有人的日志放行给一个身份不明的请求。旧写法
     * {@code isAdmin() ? null : getUserId()} 把「管理员放行」与「userId 缺失」挤到同一个 null 上，
     * 是越权隐患。
     * </p>
     */
    private Long visibilityFilterUserId() {
        if (TenantContext.isAdmin()) {
            return null;
        }
        Long userId = TenantContext.getUserId();
        AssertUtils.notNull(userId, ErrorCode.UNAUTHORIZED);
        return userId;
    }

    /** 详情可见性：管理员放行，普通用户必须是日志归属人（与列表同一套口径） */
    private boolean canView(ExecutionLog log) {
        if (TenantContext.isAdmin()) {
            return true;
        }
        Long userId = TenantContext.getUserId();
        return userId != null && userId.equals(log.getUserId());
    }

    /** 获取执行详情（含节点执行日志与脚本日志） */
    public ExecutionLogVO getDetail(Long id) {
        ExecutionLog log = executionLogMapper.selectById(id);
        // 无权查看与记录不存在返回同一错误码，避免通过报错差异探测他人日志 id 是否存在
        AssertUtils.isTrue(log != null && canView(log), ErrorCode.EXEC_LOG_NOT_FOUND);
        List<ExecutionNodeLog> nodeLogs = executionNodeLogMapper.selectByExecutionLogId(id);
        ExecutionLogVO vo = ExecutionLogVO.fromEntityWithNodes(log, nodeLogs);
        // 脚本日志从独立表读（该表不受租户过滤，可见性由上面的归属校验保证）
        vo.setScriptLogs(executionScriptLogService.listByExecutionLogId(id));
        return vo;
    }
}
