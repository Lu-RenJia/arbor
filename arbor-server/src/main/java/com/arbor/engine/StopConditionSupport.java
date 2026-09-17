package com.arbor.engine;

import com.arbor.common.BusinessException;
import com.arbor.common.EnvironmentContext;
import com.arbor.common.ErrorCode;
import com.arbor.groovy.GroovyScriptEngine;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 节点停止条件的引擎侧支撑：
 * 主线节点执行成功后评估 config 中的停止条件（Groovy 表达式，内置变量 data=当前节点结果，
 * 支持 ${} 引用前置节点/全局参数），为 true 则流程以 STOPPED 状态优雅结束。
 * FlowEngine（DB 路径）与 FlowService.executeWithNodes（DTO 路径）共用，避免双写漂移。
 *
 * @author gwl
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StopConditionSupport {

    /** config 字段 - 停止条件开关 */
    public static final String FIELD_STOP_ENABLED = "stopEnabled";
    /** config 字段 - 停止条件表达式 */
    public static final String FIELD_STOP_CONDITION = "stopCondition";
    /** config 字段 - 停止提示信息 */
    public static final String FIELD_STOP_MESSAGE = "stopMessage";
    /** config 字段 - 正常停止开关 */
    public static final String FIELD_NORMAL_STOP_ENABLED = "normalStopEnabled";
    /** config 字段 - 正常停止表达式 */
    public static final String FIELD_NORMAL_STOP_CONDITION = "normalStopCondition";
    /** config 字段 - 正常停止提示信息 */
    public static final String FIELD_NORMAL_STOP_MESSAGE = "normalStopMessage";
    /** 停止类型 - 正常停止 */
    public static final String STOP_TYPE_NORMAL = "NORMAL";
    /** 停止类型 - 异常停止 */
    public static final String STOP_TYPE_ABNORMAL = "ABNORMAL";
    /** 停止提示信息为空时的默认值 */
    public static final String DEFAULT_STOP_MESSAGE = "满足停止条件，流程停止";

    /** Groovy 编译缓存的脚本标识（同表达式内容只编译一次） */
    private static final String SCRIPT_ID = "stop-condition";

    private final ParamResolver paramResolver;
    private final GroovyScriptEngine groovyScriptEngine;
    private final ObjectMapper objectMapper;

    /**
     * 从节点 config 中剥离停止条件三字段，供节点执行前的 ${} 预解析使用。
     * 停止条件表达式可能引用当前节点自身（执行前尚未产生结果），若不剥离预解析会报错。
     * 不含停止条件字段的 config 原样返回（零开销）。
     *
     * @param rawConfig 节点原始 config JSON 字符串
     * @return 剥离后的 config JSON 字符串
     */
    public String stripStopFields(String rawConfig) {
        if (rawConfig == null || (!rawConfig.contains(FIELD_STOP_ENABLED)
                && !rawConfig.contains(FIELD_STOP_CONDITION)
                && !rawConfig.contains(FIELD_NORMAL_STOP_ENABLED)
                && !rawConfig.contains(FIELD_NORMAL_STOP_CONDITION))) {
            return rawConfig;
        }
        try {
            Map<String, Object> config = objectMapper.readValue(rawConfig, Map.class);
            config.remove(FIELD_STOP_ENABLED);
            config.remove(FIELD_STOP_CONDITION);
            config.remove(FIELD_STOP_MESSAGE);
            config.remove(FIELD_NORMAL_STOP_ENABLED);
            config.remove(FIELD_NORMAL_STOP_CONDITION);
            config.remove(FIELD_NORMAL_STOP_MESSAGE);
            return objectMapper.writeValueAsString(config);
        } catch (Exception e) {
            log.warn("剥离停止条件字段失败，config 原样返回: {}", e.getMessage());
            return rawConfig;
        }
    }

    /**
     * 评估节点的停止条件（节点执行成功后调用，此时当前节点已在 previousResults 中）。
     * 先评估异常停止，触发则直接返回；未触发再评估正常停止。
     *
     * @param rawConfig       节点原始 config JSON 字符串（未做 ${} 解析）
     * @param current         当前节点的执行结果（表达式内置变量 data 的来源）
     * @param globalParams    全局参数
     * @param previousResults 已执行节点结果（解析表达式与提示信息中的 ${}）
     * @return 触发停止时返回 StopResult；未开启/未触发返回 null
     * @throws BusinessException 表达式解析或执行出错（调用方按节点失败处理）
     */
    public StopResult evaluate(String rawConfig, NodeResult current,
                               Map<String, String> globalParams, Map<String, NodeResult> previousResults) {
        // 1. 先评估异常停止条件
        StopResult abnormal = evaluateByType(rawConfig, current, globalParams, previousResults,
                FIELD_STOP_ENABLED, FIELD_STOP_CONDITION, FIELD_STOP_MESSAGE, STOP_TYPE_ABNORMAL);
        if (abnormal != null) {
            return abnormal;
        }
        // 2. 再评估正常停止条件
        return evaluateByType(rawConfig, current, globalParams, previousResults,
                FIELD_NORMAL_STOP_ENABLED, FIELD_NORMAL_STOP_CONDITION, FIELD_NORMAL_STOP_MESSAGE, STOP_TYPE_NORMAL);
    }

    /**
     * 按类型评估停止条件
     */
    private StopResult evaluateByType(String rawConfig, NodeResult current,
                                      Map<String, String> globalParams, Map<String, NodeResult> previousResults,
                                      String enabledField, String conditionField, String messageField, String type) {
        StopConfig stopConfig = parseStopConfig(rawConfig, enabledField, conditionField, messageField);
        if (stopConfig == null || !stopConfig.enabled || stopConfig.condition == null || stopConfig.condition.isBlank()) {
            return null;
        }

        boolean triggered;
        try {
            String resolvedExpr = paramResolver.resolve(stopConfig.condition, globalParams, previousResults);
            String script = "def execute() {\n    return (" + resolvedExpr + ") ? true : false\n}";
            Map<String, Object> variables = new HashMap<>(4);
            variables.put("data", current.getData());
            // 内置变量 _env/_env_code 供停止条件写裸变量（${_env} 形式已在上一行走 ParamResolver 文本替换）
            // 未设置环境时不注入：表达式里写了裸 _env 会得到 MissingPropertyException，
            // 比注入 null 引发莫名其妙的比较失败更容易定位
            Long envId = EnvironmentContext.getEnvId();
            if (envId != null) {
                variables.put(BuiltinVariable.ENV_ID, envId);
                variables.put(BuiltinVariable.ENV_CODE, EnvironmentContext.getEnvCode());
            }
            Object result = groovyScriptEngine.execute(SCRIPT_ID, script, variables);
            triggered = Boolean.TRUE.equals(result);
        } catch (Exception e) {
            String msg = e instanceof BusinessException be ? be.getMessage() : String.valueOf(e.getMessage());
            throw new BusinessException(ErrorCode.STOP_CONDITION_EXPR_FAILED,
                    current.getNodeName(), current.getNodeCode(), msg);
        }
        if (!triggered) {
            return null;
        }

        String message = stopConfig.message == null || stopConfig.message.isBlank()
                ? DEFAULT_STOP_MESSAGE : stopConfig.message;
        try {
            message = paramResolver.resolve(message, globalParams, previousResults);
        } catch (Exception e) {
            log.warn("停止提示信息占位符解析失败，按原文返回: {}", e.getMessage());
        }
        log.info("节点触发{}停止条件: code={}, message={}",
                STOP_TYPE_NORMAL.equals(type) ? "正常" : "异常", current.getNodeCode(), message);
        return new StopResult(message, type);
    }

    /**
     * 解析 config 中指定类型的停止条件字段
     */
    private StopConfig parseStopConfig(String rawConfig, String enabledField, String conditionField, String messageField) {
        if (rawConfig == null || !rawConfig.contains(enabledField)) {
            return null;
        }
        try {
            Map<String, Object> config = objectMapper.readValue(rawConfig, Map.class);
            StopConfig sc = new StopConfig();
            sc.enabled = Boolean.TRUE.equals(config.get(enabledField));
            Object cond = config.get(conditionField);
            sc.condition = cond != null ? String.valueOf(cond) : null;
            Object msg = config.get(messageField);
            sc.message = msg != null ? String.valueOf(msg) : null;
            return sc;
        } catch (Exception e) {
            log.warn("解析停止条件配置失败: {}", e.getMessage());
            return null;
        }
    }

    /** 停止条件评估结果 */
    public static class StopResult {
        private final String message;
        private final String type;

        public StopResult(String message, String type) {
            this.message = message;
            this.type = type;
        }

        public String getMessage() { return message; }
        public String getType() { return type; }
    }

    /** 停止条件配置（config 内部字段的解析结果） */
    private static class StopConfig {
        private boolean enabled;
        private String condition;
        private String message;
    }
}
