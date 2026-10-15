package com.arbor.engine;

import com.arbor.common.AssertUtils;
import com.arbor.common.BusinessException;
import com.arbor.common.EnvironmentContext;
import com.arbor.common.ErrorCode;
import com.arbor.datasource.ParameterizedSql;
import com.arbor.datasource.SqlParameterizer;
import com.arbor.engine.executor.StartNodeExecutor;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 参数解析引擎
 * 支持 ${globalParam} 全局参数、${nodeCode} 节点结果引用、${nodeCode.field} 链式取值、
 * ${_env}/${_env_code} 引擎内置变量（见 {@link BuiltinVariable}）
 * <p>两种输出形态共用同一份取值逻辑 {@code resolveExpressionValue}，避免两条路径行为分叉：</p>
 * <ul>
 *   <li>{@link #resolve} 内联成字符串 —— 用于 URL、请求头、请求体、Redis key 等非 SQL 模板</li>
 *   <li>{@link #resolveParameterized} 产出 ? 占位 + 参数值 —— 用于 SQL，交给 PreparedStatement 绑定</li>
 * </ul>
 *
 * @author gwl
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ParamResolver {

    /** ${xxx} 占位符匹配，支持点号路径和数组索引：node1.data[0].name */
    private static final Pattern PARAM_PATTERN =
            Pattern.compile("\\$\\{([a-zA-Z_]\\w*(?:\\[\\d+\\])?(?:\\.[a-zA-Z_]\\w*(?:\\[\\d+\\])?)*)\\}");

    private final ObjectMapper objectMapper;

    /**
     * 解析模板字符串中的所有 ${xxx} 占位符
     * 优先级：引擎内置变量（保留名，不可遮蔽） > 节点数据引用 > 全局参数
     *
     * @param template        模板字符串
     * @param globalParams    全局参数 Map
     * @param previousResults 前置节点结果 Map
     * @return 解析后的字符串
     */
    public String resolve(String template, Map<String, String> globalParams,
                          Map<String, NodeResult> previousResults) {
        if (template == null || !template.contains("${")) {
            return template;
        }

        Matcher matcher = PARAM_PATTERN.matcher(template);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String expression = matcher.group(1);
            Object value = resolveExpressionValue(expression, globalParams, previousResults);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(convertToString(value)));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /**
     * 参数化解析 SQL 模板中的所有 ${xxx} 占位符，返回 ParameterizedSql（? 占位 + 参数值列表）
     * <p>
     * 与 {@link #resolve} 不同，此方法不将值内联到 SQL 字符串中，而是交给
     * {@link SqlParameterizer} 按 SQL 词法上下文改写：值位置用 ? 占位、字符串字面量内的
     * 占位符收敛为带前后缀的单个参数、标识符位置白名单校验后内联、注释内原样保留。
     * 无脑把每个占位符都换成 ? 会让 {@code LIKE '%${kw}%'} 与 {@code ORDER BY ${col}}
     * 这两类常见写法分别硬失败和静默失效。
     * </p>
     *
     * @param template        SQL 模板
     * @param globalParams    全局参数 Map
     * @param previousResults 前置节点结果 Map
     * @return 参数化 SQL（含 ? 占位符的 SQL + 有序参数值列表）
     */
    public ParameterizedSql resolveParameterized(String template, Map<String, String> globalParams,
                                                 Map<String, NodeResult> previousResults) {
        return SqlParameterizer.parameterize(template, PARAM_PATTERN, expression ->
                normalizeForBinding(resolveExpressionValue(expression, globalParams, previousResults)));
    }

    /**
     * 规整为 JDBC 可绑定的值
     * <p>简单类型原样保留；List 交给参数化器展开成多个 ?；其余（Map/POJO 等）退化为字符串，
     * 与旧的内联行为一致 —— 否则 setObject 会因驱动不认识该类型而报错。</p>
     */
    private static Object normalizeForBinding(Object value) {
        if (value == null || value instanceof List || value instanceof String
                || value instanceof Number || value instanceof Boolean
                || value instanceof java.util.Date || value instanceof java.time.temporal.Temporal
                || value instanceof byte[]) {
            return value;
        }
        return value.toString();
    }

    /**
     * 解析单个表达式，保留原始类型
     * 如：node1.data.name → 从前置节点结果中链式取值
     * 如：node1[0] → 从前置节点（列表）取第一个元素
     * 如：paramName → 从全局参数中取值
     * <p>{@link #resolve} 与 {@link #resolveParameterized} 均经由此方法取值，
     * 只在最后一步分别做字符串化或参数绑定，故两者的错误提示与越界守卫完全一致。</p>
     */
    private Object resolveExpressionValue(String expression, Map<String, String> globalParams,
                                          Map<String, NodeResult> previousResults) {
        // 按点号拆分：nodeCode[.field.path]
        int dotIndex = expression.indexOf('.');
        String fullRoot = dotIndex > 0 ? expression.substring(0, dotIndex) : expression;
        String fieldPath = dotIndex > 0 ? expression.substring(dotIndex + 1) : null;

        // 处理根级别的数组索引：node1[0]
        String rootKey = fullRoot;
        String rootIndex = null;
        int rootBracketIdx = fullRoot.indexOf('[');
        if (rootBracketIdx > 0) {
            rootKey = fullRoot.substring(0, rootBracketIdx);
            rootIndex = fullRoot.substring(rootBracketIdx + 1, fullRoot.indexOf(']', rootBracketIdx));
        }

        // 0. 引擎内置变量（保留名）：先于节点编码与全局参数匹配，保证不可被同名遮蔽
        // _env / _env_code 是标量，不支持 ${_env.foo} / ${_env[0]} —— 畸形引用必须显式拒绝，
        // 否则会把写错的引用静默当成合法值返回，这类问题最难排查
        if (BuiltinVariable.isBuiltin(rootKey)) {
            AssertUtils.isTrue(rootIndex == null && fieldPath == null,
                    ErrorCode.BUILTIN_VAR_NO_FIELD, rootKey);
            return BuiltinVariable.resolve(rootKey);
        }

        // 1. 尝试从节点结果引用中取值
        if (previousResults != null && previousResults.containsKey(rootKey)) {
            NodeResult nodeResult = previousResults.get(rootKey);
            AssertUtils.isTrue(nodeResult.isSuccess(), ErrorCode.NODE_RESULT_REF_FAILED, rootKey);
            Object data = nodeResult.getData();

            // 根级索引与嵌套路径索引采用同一套校验：不是列表、下标越界都给出可读错误，
            // 而不是抛出裸的 IndexOutOfBoundsException
            if (rootIndex != null && data != null) {
                AssertUtils.isTrue(data instanceof List, ErrorCode.PARAM_ARRAY_NOT_LIST, rootKey);
                List<?> list = (List<?>) data;
                int index = Integer.parseInt(rootIndex);
                AssertUtils.isFalse(index < 0 || index >= list.size(),
                        ErrorCode.PARAM_ARRAY_INDEX_OOB, rootKey, index, list.size());
                data = list.get(index);
            }

            if (fieldPath != null && data != null) {
                return extractFieldValue(data, fieldPath);
            }
            return data != null ? data : "";
        }

        // 2. 从全局参数中取值
        if (globalParams != null && globalParams.containsKey(expression)) {
            return globalParams.get(expression);
        }

        // 3. 未解析到，给出明确提示（含环境信息）
        AssertUtils.isFalse(StartNodeExecutor.FLOW_PARAM_KEY.equals(rootKey), ErrorCode.FLOW_PARAM_NOT_SET);
        String envHint = "";
        Long envId = EnvironmentContext.getEnvId();
        if (envId != null) {
            envHint = "（当前环境ID=" + envId + "，请检查该参数是否已在此环境下配置）";
        }
        throw new BusinessException(ErrorCode.PARAM_UNRESOLVABLE, expression, envHint);
    }

    /**
     * 从对象中按路径提取字段值，保留原始类型
     * 支持 Map 的 key 访问和 POJO 的属性访问；字段不存在、数组越界时抛出带完整已走路径的错误
     */
    private Object extractFieldValue(Object data, String fieldPath) {
        String[] parts = fieldPath.split("\\.");
        Object current = data;
        String walked = "";

        for (String part : parts) {
            AssertUtils.notNull(current, ErrorCode.PARAM_FIELD_NULL, walked);
            int bracketIdx = part.indexOf('[');
            if (bracketIdx > 0) {
                String fieldName = part.substring(0, bracketIdx);
                String indexStr = part.substring(bracketIdx + 1, part.indexOf(']'));
                Object next = getField(current, fieldName);
                walked = walked.isEmpty() ? fieldName : walked + "." + fieldName;
                AssertUtils.notNull(next, ErrorCode.PARAM_FIELD_NOT_FOUND, walked);
                AssertUtils.isTrue(next instanceof List, ErrorCode.PARAM_ARRAY_NOT_LIST, walked);
                int index = Integer.parseInt(indexStr);
                List<?> list = (List<?>) next;
                AssertUtils.isFalse(index < 0 || index >= list.size(), ErrorCode.PARAM_ARRAY_INDEX_OOB, walked, index, list.size());
                current = list.get(index);
                walked += "[" + index + "]";
            } else {
                current = getField(current, part);
                walked = walked.isEmpty() ? part : walked + "." + part;
                AssertUtils.notNull(current, ErrorCode.PARAM_FIELD_NOT_FOUND, walked);
            }
        }
        return current;
    }

    /** 从 Map 或 POJO 中获取字段值，不存在则返回 null */
    @SuppressWarnings("unchecked")
    private Object getField(Object obj, String fieldName) {
        if (obj instanceof Map) {
            return ((Map<String, Object>) obj).get(fieldName);
        }
        // 字符串形态的值若本身是 JSON 文本，先解析再取字段。
        // _input 有两种形态：普通字符串（用 ${_input} 直接引用）与 JSON（用 ${_input.xxx} 取字段）。
        // JSON 判定此前只做在 ApplicationService（应用执行入口），手动执行 / REF_FLOW 子流程 /
        // 定时任务都不经那里，届时 _input 仍是 JSON 文本，取字段会退化成裸的 Jackson 构造异常。
        if (obj instanceof String text) {
            return parseJsonObject(text).get(fieldName);
        }
        Map<String, Object> map = objectMapper.convertValue(obj, Map.class);
        return map.get(fieldName);
    }

    /**
     * JSON 文本 → Map；非 JSON 形态或解析失败返回空 Map
     * <p>刻意不抛异常：取字段失败应交给调用方给出"字段不存在"的可读错误
     * （{@link #extractFieldValue} 的 PARAM_FIELD_NOT_FOUND），
     * 而不是把 Jackson 的构造异常透给用户。</p>
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJsonObject(String text) {
        String trimmed = text.trim();
        if (trimmed.isEmpty() || (trimmed.charAt(0) != '{' && trimmed.charAt(0) != '[')) {
            return Map.of();
        }
        try {
            Object parsed = objectMapper.readValue(trimmed, Object.class);
            return parsed instanceof Map ? (Map<String, Object>) parsed : Map.of();
        } catch (Exception ignored) {
            // 形似 JSON 但解析失败：当作普通字符串处理，返回空 Map 让上层报"字段不存在"
            return Map.of();
        }
    }

    /** 将对象转为字符串（直接 toString，避开 Jackson 序列化导致的二次转义） */
    private String convertToString(Object obj) {
        return obj == null ? "" : obj.toString();
    }
}
