package com.arbor.engine;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 流程执行结果汇总
 *
 * @author gwl
 */
@Data
@Builder
public class FlowExecutionResult {

    /** 流程ID */
    private Long flowId;

    /** 流程名称 */
    private String flowName;

    /** 执行方式 */
    private String executeType;

    /** 是否成功 */
    private boolean success;

    /** 流程状态：SUCCESS/FAIL/STOPPED（STOPPED = 节点触发停止条件优雅结束，success 仍为 true） */
    private String status;

    /** 流程停止原因（仅 status=STOPPED 时有值） */
    private String stopMessage;

    /** 停止类型：NORMAL=正常停止 / ABNORMAL=异常停止（仅 status=STOPPED 时有值） */
    private String stopType;

    /** 输出文件信息（仅 outputType=FILE 时有值） */
    private OutputFileInfo outputFile;

    /** 开始时间 */
    private LocalDateTime startTime;

    /** 结束时间 */
    private LocalDateTime endTime;

    /** 耗时（毫秒） */
    private long duration;

    /** 错误信息 */
    private String errorMessage;

    /** 节点执行详情列表（按执行顺序，含节点编码、耗时和返回数据，供前端展示和匹配） */
    private List<NodeResult> nodeDetailList;

    /**
     * 本次执行捕获的 Groovy 脚本日志，按时间顺序的扁平列表（子流程内部节点的日志已冒泡进来）。
     * <p>
     * 结构与应用执行路径自己落库的 {@code execution_script_log} 一致，
     * 前端日志控制台在设计器与历史详情两处复用同一套渲染。
     * </p>
     */
    private List<com.arbor.groovy.log.ScriptLogEntry> scriptLogs;

    /** 输出文件信息 */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OutputFileInfo {
        /** 文件名 */
        private String fileName;
        /** 下载ID（对应 file_resource.id） */
        private Long fileId;
        /** 文件大小（字节） */
        private Long size;
        /** 文件大小可读文本（已换算单位，如 1.5 MB） */
        private String sizeText;
    }
}
