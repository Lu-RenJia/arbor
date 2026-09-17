package com.arbor.service;

import com.arbor.common.TenantContext;
import com.arbor.entity.ExecutionScriptLog;
import com.arbor.groovy.log.ScriptLogEntry;
import com.arbor.mapper.ExecutionScriptLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 脚本执行日志读写服务。
 * <p>
 * 三个落库点（{@code FlowEngine} / {@code FlowDtoExecutionService} / {@code ApplicationService}）
 * 与详情查询共用这里的转换与分片逻辑，避免同一套「条目 ↔ 实体」映射写三遍。
 * </p>
 *
 * @author gwl
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExecutionScriptLogService {

    /** 单条 SQL 的最大行数，超出分片提交，避免 SQL 过长触发 max_allowed_packet */
    private static final int BATCH_SIZE = 500;

    /** 对外统一的时间格式，与 {@link com.arbor.groovy.log.ScriptLogCollector} 保持一致 */
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    private final ExecutionScriptLogMapper mapper;

    /**
     * 把一次执行收集到的脚本日志落库。
     * <p>
     * 失败只记 error、不外抛 —— 日志属附属信息，写不进去绝不能影响执行结果，
     * 也不能让调用方所在的事务回滚掉主日志与节点日志。
     * </p>
     *
     * @param executionLogId 所属执行日志ID
     * @param entries        本次执行捕获的日志条目（按时间顺序）
     */
    public void save(Long executionLogId, List<ScriptLogEntry> entries) {
        if (executionLogId == null || entries == null || entries.isEmpty()) {
            return;
        }
        try {
            Long userId = TenantContext.getUserId();
            List<ExecutionScriptLog> rows = new ArrayList<>(entries.size());
            int sortOrder = 0;
            for (ScriptLogEntry entry : entries) {
                rows.add(toEntity(executionLogId, entry, ++sortOrder, userId));
            }
            for (int from = 0; from < rows.size(); from += BATCH_SIZE) {
                int to = Math.min(from + BATCH_SIZE, rows.size());
                mapper.insertBatch(rows.subList(from, to));
            }
        } catch (Exception e) {
            log.error("写入脚本执行日志失败: executionLogId={}, 条数={}", executionLogId, entries.size(), e);
        }
    }

    /**
     * 查某次执行的脚本日志并映射成对外条目。
     * <p>
     * 结构与设计器执行结果里的 {@code scriptLogs} 完全一致，前端 LogConsole 两处复用。
     * </p>
     */
    public List<ScriptLogEntry> listByExecutionLogId(Long executionLogId) {
        if (executionLogId == null) {
            return List.of();
        }
        List<ExecutionScriptLog> rows = mapper.selectByExecutionLogId(executionLogId);
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        List<ScriptLogEntry> entries = new ArrayList<>(rows.size());
        for (ExecutionScriptLog row : rows) {
            entries.add(ScriptLogEntry.builder()
                    .level(row.getLogLevel())
                    // 库里存完整时间戳，对外只给时分秒毫秒，与设计器返回的结构一致
                    .time(row.getLogTime() != null ? row.getLogTime().format(TIME_FORMATTER) : null)
                    .logger(row.getLogger())
                    .nodeCode(row.getNodeCode())
                    .nodeName(row.getNodeName())
                    .message(row.getMessage())
                    .build());
        }
        return entries;
    }

    private ExecutionScriptLog toEntity(Long executionLogId, ScriptLogEntry entry, int sortOrder, Long userId) {
        ExecutionScriptLog row = new ExecutionScriptLog();
        row.setExecutionLogId(executionLogId);
        row.setNodeCode(entry.getNodeCode());
        row.setNodeName(entry.getNodeName());
        row.setLogLevel(entry.getLevel());
        row.setLogger(entry.getLogger());
        // 条目自带完整时间戳；缺失时兜底当前时间，避免 NOT NULL 列插入失败
        row.setLogTime(entry.getTimestamp() != null ? entry.getTimestamp() : java.time.LocalDateTime.now());
        row.setMessage(entry.getMessage());
        row.setSortOrder(sortOrder);
        // 本表在多租户白名单中，插件不自动补 user_id；该列仅作追溯用，不参与过滤
        row.setUserId(userId != null ? userId : 0L);
        return row;
    }
}
