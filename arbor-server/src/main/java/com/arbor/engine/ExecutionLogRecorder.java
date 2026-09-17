package com.arbor.engine;

import com.arbor.common.TenantContext;
import com.arbor.entity.ExecutionLog;
import com.arbor.mapper.ExecutionLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 执行日志统一写入工具。
 * 供各类脚本/API/Redis 独立测试方法在返回前写入 execution_log 表，
 * 不写 node_log（单次原子操作无流程节点概念）。
 *
 * @author gwl
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExecutionLogRecorder {

    private final ExecutionLogMapper executionLogMapper;

    /**
     * 写入一条执行日志。
     *
     * @param flowId     关联的流程ID，可为 null（独立测试不关联流程）
     * @param flowName   流程/脚本名称，为 null 时默认"独立测试"
     * @param source     日志来源（使用 FlowEngine.SOURCE_* 常量）
     * @param input      入参描述，可为 null
     * @param status     执行状态：SUCCESS / FAIL
     * @param durationMs 执行耗时（毫秒）
     * @param errorMsg   错误信息，成功时为 null
     */
    public void save(Long flowId, String flowName, String source,
                     String input, String status, long durationMs, String errorMsg) {
        try {
            ExecutionLog execLog = new ExecutionLog();
            execLog.setFlowId(flowId != null ? flowId : 0L);
            execLog.setFlowName(flowName != null ? flowName : "独立测试");
            execLog.setUserId(TenantContext.getUserId());
            execLog.setExecuteType(FlowEngine.EXECUTE_MANUAL);
            execLog.setSource(source);
            execLog.setOwnerId(TenantContext.getUserId());
            execLog.setExecutorId(TenantContext.getUserId());
            // input_params 是 JSON 列，只接受合法 JSON；非 JSON 字符串不写入
            execLog.setInputParams(toJsonOrNull(input));
            execLog.setStatus(status);
            execLog.setStartTime(LocalDateTime.now().minusNanos(durationMs * 1_000_000L));
            execLog.setEndTime(LocalDateTime.now());
            execLog.setDuration(durationMs);
            execLog.setErrorMessage(errorMsg);
            executionLogMapper.insert(execLog);
        } catch (Exception e) {
            log.error("记录执行日志失败", e);
        }
    }

    /**
     * 安全转为 JSON 列可接受的值：合法 JSON 对象/数组原样返回，否则返回 null。
     * execution_log.input_params 是 JSON 类型列，不能写入普通字符串。
     */
    private String toJsonOrNull(String input) {
        if (input == null) return null;
        String trimmed = input.trim();
        if ((trimmed.startsWith("{") || trimmed.startsWith("[")) && trimmed.length() > 2) {
            return trimmed;
        }
        return null;
    }
}
