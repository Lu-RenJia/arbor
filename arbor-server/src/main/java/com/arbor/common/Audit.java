package com.arbor.common;

import java.lang.annotation.*;

/**
 * 操作审计注解 — 标注在 Controller 方法上，AOP 切面自动写入 audit_log 表。
 * <p>
 * 登录/登出等无 SecurityContext 的场景由 AuthController 显式调用 AuditLogService 记录。
 * </p>
 *
 * @author gwl
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Audit {

    /** 业务模块，如 "数据源"、"用户"、"角色" */
    String module();

    /** 操作类型：CREATE / UPDATE / DELETE / IMPORT / EXPORT 等 */
    String action();

    /** 操作描述（可选，默认取 模块+操作） */
    String description() default "";
}
