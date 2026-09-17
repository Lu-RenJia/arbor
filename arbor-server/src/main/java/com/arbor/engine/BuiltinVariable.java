package com.arbor.engine;

import com.arbor.common.AssertUtils;
import com.arbor.common.EnvironmentContext;
import com.arbor.common.ErrorCode;
import com.arbor.engine.executor.StartNodeExecutor;

import java.util.Set;

/**
 * 引擎内置变量 — 无需在变量池中声明，模板 ${} 与 Groovy Binding 双通道可用
 * <p>
 * 与 {@code _input}、{@code _fileStream} 这类由节点产物注入的变量不同，
 * 本类的变量取自 {@link EnvironmentContext}（ThreadLocal），与节点执行无关，
 * 因此在流程执行链路中"任何位置"都能取到值。
 * </p>
 * <p>
 * 与 {@code _input} 的另一处差异：{@code _input} 在 {@link ParamResolver} 中是
 * <b>后置</b>匹配（同名节点编码可遮蔽它），而本类变量是<b>前置</b>保留名，
 * 不可被同名节点编码或全局参数遮蔽 —— 节点编码在前端可自由编辑且无校验，
 * 若允许遮蔽会让写错编码的流程静默拿到环境值。
 * </p>
 * <p>
 * 取值来源 {@code EnvironmentContext} 的环境编码受 {@code EnvironmentDTO} 的
 * {@code @Pattern("[a-zA-Z][a-zA-Z0-9_-]*")} 约束，不含引号、反斜杠与 {@code $}，
 * 故 {@link #ENV_CODE} 作为文本嵌入 Groovy 源码或 JSON config 是安全的（非注入风险）。
 * </p>
 *
 * @author gwl
 */
public final class BuiltinVariable {

    /** 当前执行环境ID，类型 Long（SQL 参数化时绑为 BIGINT，避免隐式转换导致索引失效） */
    public static final String ENV_ID = "_env";

    /** 当前执行环境编码，类型 String */
    public static final String ENV_CODE = "_env_code";

    /** 内置/保留名全集，供导出扫描排除等场景复用 */
    public static final Set<String> ALL = Set.of(
            StartNodeExecutor.FLOW_PARAM_KEY, "flow_param", "_fileStream", "_fileName", ENV_ID, ENV_CODE);

    private BuiltinVariable() {}

    /**
     * 是否为可解析的内置变量（保留名）
     * <p>必须精确匹配：{@code startsWith} 会误吞 {@code _env_backup} 这类用户自定义变量。</p>
     */
    public static boolean isBuiltin(String name) {
        return ENV_ID.equals(name) || ENV_CODE.equals(name);
    }

    /**
     * 取内置变量值，保留原始类型
     * <p>
     * 环境未设置时<b>直接报错</b>：不回退用户默认环境、不返回空串。
     * 静默返回空串会让 SQL 里的 {@code WHERE env_id = ${_env}} 变成参数 null 而查空、
     * Redis key 变成空串，问题被藏起来。正常执行的三条入口均会先解析默认环境再 set 上下文，
     * 故此分支实为防御网（也是单测唯一能覆盖的分支）。
     * </p>
     *
     * @param name 内置变量名（须先经 {@link #isBuiltin} 判定）
     * @return 环境ID（Long）或环境编码（String）
     */
    public static Object resolve(String name) {
        if (ENV_ID.equals(name)) {
            Long envId = EnvironmentContext.getEnvId();
            AssertUtils.notNull(envId, ErrorCode.ENV_CONTEXT_NOT_SET, ENV_ID);
            return envId;
        }
        String envCode = EnvironmentContext.getEnvCode();
        AssertUtils.hasText(envCode, ErrorCode.ENV_CONTEXT_NOT_SET, ENV_CODE);
        return envCode;
    }
}
