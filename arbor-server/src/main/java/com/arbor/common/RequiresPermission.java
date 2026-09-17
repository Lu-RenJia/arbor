package com.arbor.common;

import java.lang.annotation.*;

/**
 * 权限校验注解
 * <p>
 * 标注在 Controller 方法上，AOP 切面自动校验当前用户是否拥有指定权限码。
 * </p>
 *
 * @author gwl
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequiresPermission {

    /** 权限码，如 "system:user:list" */
    String value();

    /** 逻辑：ALL（拥有全部权限才通过）、ANY（拥有任一权限即通过），默认 ALL */
    Logical logical() default Logical.ALL;

    enum Logical {
        ALL, ANY
    }
}
