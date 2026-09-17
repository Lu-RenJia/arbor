package com.arbor.groovy.log;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 脚本日志条目 —— 一次流程执行中由 Groovy 脚本打出的一行日志。
 * <p>
 * 既用于内存态收集（{@link ScriptLogCollector}），也是返回前端与落库
 * {@code execution_script_log} 的统一载体，设计器与执行历史详情两处结构一致，
 * 前端 {@code LogConsole} 才能复用同一套渲染逻辑。
 * </p>
 * <p>
 * {@code time} 由服务端格式化成 {@code HH:mm:ss.SSS} 后再返回，前端直接显示、不做二次转换。
 * </p>
 *
 * @author gwl
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScriptLogEntry {

    /** 级别：DEBUG / INFO / WARN / ERROR（TRACE 不捕获） */
    private String level;

    /** 打印时间，已格式化为 HH:mm:ss.SSS */
    private String time;

    /** logger 名，默认取节点名称，一眼看出这行日志来自哪个节点 */
    private String logger;

    /** 节点编码（备用，供筛选与落库） */
    private String nodeCode;

    /** 节点名称（备用，供筛选与落库） */
    private String nodeName;

    /** 日志内容，{} 占位符已替换的最终文本 */
    private String message;

    /**
     * 完整时间戳，仅供落库 {@code execution_script_log.log_time}（DATETIME(3)）使用。
     * <p>
     * {@link #time} 只有时分秒毫秒，拿它反推日期在跨零点执行时会错一天，故单独存一份；
     * 标注 {@code @JsonIgnore} 以保持对外 JSON 结构与前端约定一致
     * （{@code level/time/logger/nodeCode/nodeName/message}）。
     * </p>
     */
    @JsonIgnore
    private LocalDateTime timestamp;
}
