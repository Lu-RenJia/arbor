package com.arbor.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 脚本执行日志实体 —— 对应 {@code execution_script_log}，一行一条 Groovy 脚本日志。
 * <p>
 * 本表不参与多租户过滤（已加入 {@code MybatisPlusConfig.SHARED_TABLES}），
 * 可见性完全由受租户过滤的 {@code execution_log} 决定；{@code userId} 为保留列，新行恒为 0。
 * </p>
 *
 * @author gwl
 */
@Data
public class ExecutionScriptLog {

    private Long id;
    /** 关联的执行日志ID */
    private Long executionLogId;
    /** 节点编码 */
    private String nodeCode;
    /** 节点名称 */
    private String nodeName;
    /** 级别: DEBUG/INFO/WARN/ERROR */
    private String logLevel;
    /** logger 名（= 节点名称） */
    private String logger;
    /** 打印时间（毫秒精度） */
    private LocalDateTime logTime;
    /** 日志内容（占位符已替换） */
    private String message;
    /** 本次执行内的顺序（1-based） */
    private Integer sortOrder;
    /** 所属用户ID（保留列，不参与租户过滤） */
    private Long userId;
    /** 创建时间 */
    private LocalDateTime createTime;
}
