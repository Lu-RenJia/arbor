package com.arbor.datasource;

import com.arbor.common.BusinessException;
import com.arbor.common.ErrorCode;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.StringJoiner;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SQL 模板参数化器：把 {@code ${expr}} 占位符改写成 JDBC 的 {@code ?} 并收集参数值
 * <p>
 * 不能无脑把每个占位符都替换成 {@code ?}，因为 JDBC 驱动只把「字符串字面量之外」的 {@code ?}
 * 当作参数标记，而标识符位置（表名/列名/排序字段）根本不允许用 {@code ?} 占位。
 * 因此本类按 SQL 词法上下文分别处理：
 * </p>
 * <table border="1">
 *   <caption>占位符上下文与处理方式</caption>
 *   <tr><th>上下文</th><th>处理</th><th>示例</th></tr>
 *   <tr><td>单引号字面量内</td><td>整个字面量改写为一个 ?，值 = 字面量各段与解析结果拼接</td>
 *       <td>{@code LIKE '%${kw}%'} → {@code LIKE ?}，参数 {@code %关键字%}</td></tr>
 *   <tr><td>标识符位置</td><td>白名单校验后内联（? 无法占位标识符）</td>
 *       <td>{@code ORDER BY ${col}} → {@code ORDER BY create_time}</td></tr>
 *   <tr><td>注释内</td><td>原样保留、不产生参数</td>
 *       <td>{@code -- ${note}} 保持不变</td></tr>
 *   <tr><td>双引号 / 反引号内</td><td>抛错</td>
 *       <td>双引号语义因数据库而异，不做猜测</td></tr>
 *   <tr><td>其余（值位置）</td><td>? 占位 + 绑定</td>
 *       <td>{@code WHERE id = ${id}} → {@code WHERE id = ?}</td></tr>
 * </table>
 * <p>
 * 列表值在值位置展开为多个 {@code ?}（{@code IN (${ids})} → {@code IN (?,?,?)}）；
 * 空列表直接报错，因为 {@code IN ()} 是非法 SQL。
 * </p>
 * <p>
 * 产出后做一次自检：用 {@link #countMarkers} 独立数一遍生成 SQL 里的标记数，
 * 必须与参数个数相等，否则抛错。这样模板里手写的多余 {@code ?} 会在解析期就暴露，
 * 而不是等到驱动抛出含义模糊的 "Parameter index out of range"。
 * </p>
 *
 * @author gwl
 */
public final class SqlParameterizer {

    /** 后置标识符的关键字：出现后到子句结束前，占位符都处于标识符位置 */
    private static final Set<String> IDENT_KEYWORDS =
            Set.of("FROM", "JOIN", "INTO", "UPDATE", "TABLE", "BY");

    /** 结束标识符子句的关键字：出现后占位符回到值位置 */
    private static final Set<String> CLAUSE_END_KEYWORDS = Set.of(
            "WHERE", "SET", "VALUES", "HAVING", "LIMIT", "OFFSET", "FETCH",
            "UNION", "EXCEPT", "INTERSECT", "MINUS", "ON", "SELECT", "AND", "OR", "RETURNING");

    /**
     * 可安全内联到标识符位置的白名单：标识符（可带库名前缀）、纯数字（ORDER BY 的列序号）、
     * ASC/DESC 修饰，以及以上元素的逗号列表。
     * <p>不含引号、括号、分号、空格以外的任何字符与运算符，故内联后无法改变 SQL 结构。</p>
     */
    private static final String IDENT = "[A-Za-z_][A-Za-z0-9_]*(?:\\.[A-Za-z_][A-Za-z0-9_]*)*";
    private static final String IDENT_ITEM = "(?:" + IDENT + "|[0-9]+)(?:\\s+(?i:ASC|DESC))?";
    private static final Pattern IDENTIFIER = Pattern.compile(IDENT_ITEM + "(?:\\s*,\\s*" + IDENT_ITEM + ")*");

    private SqlParameterizer() {
    }

    /**
     * 参数化 SQL 模板
     *
     * @param template         SQL 模板，可含 {@code ${expr}} 占位符
     * @param placeholderPattern 占位符正则，必须用 group(1) 捕获表达式本体
     * @param valueResolver    表达式 → 值；返回值可为 List（在值位置展开为多个 ?）
     * @return 含 ? 占位符的 SQL 与有序参数值
     * @throws BusinessException 占位符处于无法参数化的位置，或解析结果不是合法标识符
     */
    public static ParameterizedSql parameterize(String template, Pattern placeholderPattern,
                                                Function<String, Object> valueResolver) {
        if (template == null || template.isEmpty()) {
            return ParameterizedSql.of(template);
        }

        List<int[]> spans = new ArrayList<>();
        List<String> expressions = new ArrayList<>();
        Matcher matcher = placeholderPattern.matcher(template);
        while (matcher.find()) {
            spans.add(new int[]{matcher.start(), matcher.end()});
            expressions.add(matcher.groupCount() >= 1 ? matcher.group(1) : stripBraces(matcher.group()));
        }

        StringBuilder out = new StringBuilder(template.length() + 16);
        List<Object> params = new ArrayList<>();
        StringBuilder word = new StringBuilder();
        boolean identClause = false;
        int n = template.length();
        int i = 0;
        int p = 0;

        while (i < n) {
            char c = template.charAt(i);

            // ===== 引号区间：单引号字面量 / 双引号 / 反引号标识符 =====
            if (c == '\'' || c == '"' || c == '`') {
                word.setLength(0);
                int end = findQuotedEnd(template, i, c);
                if (end < 0) {
                    // 这是模板自身的语法错误（不是占位符位置问题），故用专用错误码并给出字符位置；
                    // 此处未必有占位符落在未闭合区间内，报不出有意义的表达式名
                    throw new BusinessException(ErrorCode.SQL_UNTERMINATED_QUOTE, quoteName(c), i + 1);
                }
                List<Integer> inside = new ArrayList<>();
                while (p < spans.size() && spans.get(p)[0] < end) {
                    inside.add(p++);
                }
                if (inside.isEmpty()) {
                    out.append(template, i, end + 1);
                } else if (c == '\'') {
                    // 字面量内的占位符无法单独占位（引号内的 ? 不被驱动识别为标记），
                    // 故把整个字面量收敛为一个 ?，值 = 字面量固定片段与解析结果按序拼接
                    StringBuilder value = new StringBuilder();
                    int cursor = i + 1;
                    for (int idx : inside) {
                        int[] span = spans.get(idx);
                        value.append(unescape(template.substring(cursor, span[0])));
                        value.append(toText(valueResolver.apply(expressions.get(idx))));
                        cursor = span[1];
                    }
                    value.append(unescape(template.substring(cursor, end)));
                    params.add(value.toString());
                    out.append('?');
                } else {
                    throw new BusinessException(ErrorCode.SQL_PARAM_UNSUPPORTED_POSITION,
                            expressions.get(inside.get(0)), unsupportedQuoteReason(c));
                }
                i = end + 1;
                continue;
            }

            // ===== 行注释：-- 与 #（MySQL）。SQL 不认 //，故不在此处理 =====
            if ((c == '-' && i + 1 < n && template.charAt(i + 1) == '-') || c == '#') {
                word.setLength(0);
                int end = template.indexOf('\n', i);
                if (end < 0) {
                    end = n;
                }
                // 注释内的占位符原样保留、不产生参数：否则 ? 会被注释吞掉而参数仍被绑定
                while (p < spans.size() && spans.get(p)[0] < end) {
                    p++;
                }
                out.append(template, i, end);
                i = end;
                continue;
            }

            // ===== 块注释 /* ... */ =====
            if (c == '/' && i + 1 < n && template.charAt(i + 1) == '*') {
                word.setLength(0);
                int close = template.indexOf("*/", i + 2);
                int end = close < 0 ? n : close + 2;
                while (p < spans.size() && spans.get(p)[0] < end) {
                    p++;
                }
                out.append(template, i, end);
                i = end;
                continue;
            }

            // ===== 占位符（普通位置）=====
            if (c == '$' && p < spans.size() && spans.get(p)[0] == i) {
                String expression = expressions.get(p);
                int spanEnd = spans.get(p)[1];
                p++;
                // 占位符也终止一个词的累积，故 ORDER BY${col} 这类无空格写法同样能识别
                identClause = advanceClauseState(word, identClause);

                Object value = valueResolver.apply(expression);
                if (identClause) {
                    // 标识符位置不能用 ? 占位（ORDER BY ? 会被当作常量、静默失去排序效果），
                    // 只能白名单校验后内联；校验不过就报错，绝不放行可疑内容
                    String text = toText(value).trim();
                    if (!IDENTIFIER.matcher(text).matches()) {
                        throw new BusinessException(ErrorCode.SQL_PARAM_INVALID_IDENTIFIER, expression, text);
                    }
                    out.append(text);
                } else if (value instanceof List<?> list) {
                    if (list.isEmpty()) {
                        throw new BusinessException(ErrorCode.SQL_PARAM_UNSUPPORTED_POSITION, expression,
                                "解析结果为空列表，无法生成 IN (...) 占位符（IN () 是非法 SQL）");
                    }
                    StringJoiner joiner = new StringJoiner(",");
                    for (Object item : list) {
                        params.add(item);
                        joiner.add("?");
                    }
                    out.append(joiner);
                } else {
                    params.add(value);
                    out.append('?');
                }
                i = spanEnd;
                continue;
            }

            // ===== 普通字符：累积关键字用于判定标识符子句 =====
            if (isWordChar(c)) {
                word.append(c);
            } else {
                identClause = advanceClauseState(word, identClause);
            }
            out.append(c);
            i++;
        }

        String sql = out.toString();
        int markers = countMarkers(sql);
        if (markers != params.size()) {
            throw new BusinessException(ErrorCode.SQL_PARAM_MARKER_MISMATCH, markers, params.size());
        }
        return new ParameterizedSql(sql, params);
    }

    /**
     * 统计 SQL 中真正的 JDBC 参数标记个数（字面量与注释里的 ? 不计）
     * <p>供调用方在执行前校验「占位符个数 == 参数个数」，把驱动抛出的
     * "Parameter index out of range" 提前变成可读的错误。</p>
     */
    public static int countMarkers(String sql) {
        if (sql == null) {
            return 0;
        }
        int n = sql.length();
        int count = 0;
        int i = 0;
        while (i < n) {
            char c = sql.charAt(i);
            if (c == '\'' || c == '"' || c == '`') {
                int end = findQuotedEnd(sql, i, c);
                i = end < 0 ? n : end + 1;
                continue;
            }
            if ((c == '-' && i + 1 < n && sql.charAt(i + 1) == '-') || c == '#') {
                int end = sql.indexOf('\n', i);
                i = end < 0 ? n : end;
                continue;
            }
            if (c == '/' && i + 1 < n && sql.charAt(i + 1) == '*') {
                int close = sql.indexOf("*/", i + 2);
                i = close < 0 ? n : close + 2;
                continue;
            }
            if (c == '?') {
                count++;
            }
            i++;
        }
        return count;
    }

    /** 结算已累积的词并据此推进「是否处于标识符子句」的状态 */
    private static boolean advanceClauseState(StringBuilder word, boolean current) {
        if (word.length() == 0) {
            return current;
        }
        String upper = word.toString().toUpperCase(Locale.ROOT);
        word.setLength(0);
        if (IDENT_KEYWORDS.contains(upper)) {
            return true;
        }
        if (CLAUSE_END_KEYWORDS.contains(upper)) {
            return false;
        }
        return current;
    }

    /**
     * 查找与 start 处引号配对的结束引号下标，未闭合返回 -1
     * <p>只识别双写引号转义（{@code ''}）—— 这是标准 SQL 规则，MySQL、Oracle、PostgreSQL、
     * 达梦均适用。故意不识别 MySQL 默认开启的反斜杠转义（{@code \'}）：一旦识别，
     * {@code LIKE '%x%' ESCAPE '\\'} 这类写法会被误判为未闭合引号；而不识别时，
     * 真遇到 {@code \'} 只会得到「未闭合引号」的显式报错，不会静默算错字面量边界。</p>
     */
    private static int findQuotedEnd(String text, int start, char quote) {
        int n = text.length();
        int j = start + 1;
        while (j < n) {
            char c = text.charAt(j);
            if (c == quote) {
                if (j + 1 < n && text.charAt(j + 1) == quote) {
                    j += 2;
                    continue;
                }
                return j;
            }
            j++;
        }
        return -1;
    }

    /**
     * 把字符串字面量的固定片段还原为真实值：双写的 {@code ''} 转回一个 {@code '}
     * <p>字面量被改写为 ? 后，内容不再经过 SQL 词法解析，故必须先去掉转义，
     * 否则 {@code 'it''s ${x}'} 会绑定成 {@code it''s 值} 而不是 {@code it's 值}。</p>
     */
    private static String unescape(String literalBody) {
        return literalBody.indexOf("''") < 0 ? literalBody : literalBody.replace("''", "'");
    }

    private static boolean isWordChar(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_';
    }

    /** 与旧的内联解析保持一致：null 渲染为空串，其余用 toString（不做 JSON 序列化以免二次转义） */
    private static String toText(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static String stripBraces(String placeholder) {
        return placeholder.startsWith("${") && placeholder.endsWith("}")
                ? placeholder.substring(2, placeholder.length() - 1)
                : placeholder;
    }

    private static String quoteName(char quote) {
        return switch (quote) {
            case '\'' -> "单引号";
            case '"' -> "双引号";
            default -> "反引号";
        };
    }

    private static String unsupportedQuoteReason(char quote) {
        if (quote == '"') {
            return "位于双引号内，而双引号语义因数据库而异（MySQL 视为字符串，Oracle/PostgreSQL/达梦视为标识符），"
                    + "不做猜测；若要传入字符串值请改用单引号";
        }
        return "位于反引号标识符内，标识符位置无法用 ? 占位；请去掉反引号，例如写成 ORDER BY ${col}";
    }
}
