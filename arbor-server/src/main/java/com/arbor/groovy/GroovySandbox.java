package com.arbor.groovy;

import com.arbor.common.BusinessException;
import com.arbor.common.ErrorCode;
import org.codehaus.groovy.ast.ClassCodeExpressionTransformer;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.expr.Expression;
import org.codehaus.groovy.ast.expr.GStringExpression;
import org.codehaus.groovy.ast.expr.MethodCallExpression;
import org.codehaus.groovy.classgen.GeneratorContext;
import org.codehaus.groovy.control.CompilePhase;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.SourceUnit;
import org.codehaus.groovy.control.customizers.CompilationCustomizer;
import org.codehaus.groovy.control.customizers.SecureASTCustomizer;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Groovy 脚本沙箱 — 编译期静态限制 + 源码预扫描双层防线
 * <p>
 * 背景：用户脚本与应用运行在同一 JVM，若无限制可执行任意系统命令、读写任意文件。
 * 防线一（源码预扫描）：拦截 Groovy 扩展方法形式的命令执行（如 "cmd".execute() / ["ls"].execute()），
 * 此类调用在 AST 层无法通过 receiver 检查识别，必须文本级拦截。
 * 防线二（SecureASTCustomizer）：编译期禁止危险类的 import / 方法调用 / 构造调用，
 * 违规脚本在编译阶段即抛 SecurityException，永不进入运行期。
 * </p>
 * <p>
 * 附加改造（GStringToStringCustomizer）：编译期把所有 "${...}" 插值表达式自动改写为
 * ".toString()" 调用，脚本内双引号插值永远产出真 String 而非 GString 对象。
 * 背景：GString 裸着进 Map 后被 Jackson 序列化会退化为嵌套 Bean 结构
 * （strings/values），导致 HTTP 请求体字段变成对象、对端强转 String 报错；
 * 另有 HashMap key 取不到、.equals() 为 false 等衍生坑。Arbor 脚本场景用不到
 * GString 的惰性求值/模板能力，编译期统一降级为 String 对脚本作者透明。
 * </p>
 * <p>
 * 已知限制：纯静态分析无法穷尽所有反射绕过路径，如确需更强隔离应引入运行期拦截
 * （groovy-sandbox / Jenkins 方案）。当前双层防线可拦截绝大多数直接攻击。
 * </p>
 *
 * @author gwl
 */
public final class GroovySandbox {

    private GroovySandbox() {
    }

    /** Groovy 命令执行扩展方法：xxx.execute(...)，含点号后任意空白与换行 */
    private static final Pattern EXECUTE_CALL =
            Pattern.compile("\\.\\s*execute\\s*\\(");

    /** 源码中禁止出现的危险标识符（命令执行/反射/脚本引擎入口） */
    private static final List<String> FORBIDDEN_TOKENS = List.of(
            "ProcessBuilder", "GroovyShell", "GroovyClassLoader", "ScriptEngine");

    /** 编译期禁止调用的接收者类（方法调用 + 构造调用均拦截） */
    @SuppressWarnings("rawtypes")
    private static final List<Class> DISALLOWED_RECEIVERS = List.of(
            // 系统命令执行
            Runtime.class, ProcessBuilder.class,
            // JVM 操控：exit 终止进程、线程操控、环境信息探测
            System.class, Thread.class, ThreadGroup.class,
            // 反射 / 类加载绕过通道
            Class.class, ClassLoader.class,
            groovy.lang.GroovyClassLoader.class, groovy.lang.GroovyShell.class,
            java.lang.reflect.Method.class, java.lang.reflect.Field.class,
            java.lang.reflect.Constructor.class,
            // 直接文件访问（可读取服务器配置文件中的数据库密码等）
            java.io.File.class, java.io.FileInputStream.class, java.io.FileOutputStream.class,
            java.io.RandomAccessFile.class, java.nio.file.Files.class, java.nio.file.Paths.class,
            // 直接网络访问（数据外传通道；HTTP 调用应通过平台 API 节点）
            java.net.Socket.class, java.net.ServerSocket.class, java.net.URL.class,
            java.net.HttpURLConnection.class);

    /** 编译期禁止的包级 import（星号导入） */
    private static final List<String> DISALLOWED_STAR_IMPORTS = List.of(
            "java.lang.reflect", "java.lang.invoke", "java.nio.file",
            "java.net", "javax.script", "java.security");

    /**
     * 构建带沙箱约束的编译配置
     */
    public static CompilerConfiguration secureConfig() {
        SecureASTCustomizer secure = new SecureASTCustomizer();
        secure.setDisallowedStarImports(DISALLOWED_STAR_IMPORTS);
        secure.setDisallowedReceiversClasses(DISALLOWED_RECEIVERS);
        secure.setPackageAllowed(false);
        CompilerConfiguration config = new CompilerConfiguration();
        config.addCompilationCustomizers(secure, new GStringToStringCustomizer());
        return config;
    }

    /**
     * GString 编译期降级：遍历 AST，把每个 GStringExpression 改写为
     * {@code <gstring>.toString()} 的 MethodCallExpression，使脚本内所有双引号
     * 插值（含三引号多行、$name 无花括号形式）产出的都是 java.lang.String。
     */
    static final class GStringToStringCustomizer extends CompilationCustomizer {

        GStringToStringCustomizer() {
            super(CompilePhase.CONVERSION);
        }

        @Override
        public void call(SourceUnit source, GeneratorContext context, ClassNode classNode) {
            new GStringTransformer(source).visitClass(classNode);
        }

        private static final class GStringTransformer extends ClassCodeExpressionTransformer {

            private final SourceUnit source;

            GStringTransformer(SourceUnit source) {
                this.source = source;
            }

            @Override
            protected SourceUnit getSourceUnit() {
                return source;
            }

            @Override
            public Expression transform(Expression original) {
                // 先递归转换子表达式（嵌套 GString / 插值内部的表达式）
                Expression expr = super.transform(original);
                if (expr instanceof GStringExpression) {
                    MethodCallExpression call = new MethodCallExpression(
                            expr, "toString", MethodCallExpression.NO_ARGUMENTS);
                    call.setImplicitThis(false);
                    call.setSourcePosition(expr);
                    return call;
                }
                return expr;
            }
        }
    }

    /**
     * 源码预扫描 — 在编译前拦截 AST 无法识别的危险写法
     *
     * @param source 脚本源码
     * @throws BusinessException 命中禁止模式时抛出
     */
    public static void checkSource(String source) {
        if (source == null || source.isEmpty()) {
            return;
        }
        if (EXECUTE_CALL.matcher(source).find()) {
            throw new BusinessException(ErrorCode.GROOVY_SANDBOX_VIOLATION,
                    "调用 .execute() 执行系统命令（Groovy 命令执行扩展）；"
                            + "如需 HTTP 请求请改用平台内置工具：脚本内 http.get/postJson/postForm，"
                            + "工具类内 import com.arbor.groovy.tool.HttpTool 后调用同名静态方法");
        }
        for (String token : FORBIDDEN_TOKENS) {
            if (source.contains(token)) {
                throw new BusinessException(ErrorCode.GROOVY_SANDBOX_VIOLATION,
                        "引用危险类 " + token);
            }
        }
    }
}
