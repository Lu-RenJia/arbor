package com.arbor.common;

/**
 * 业务断言工具 — 统一校验风格，替代 if...throw 冗余代码
 *
 * @author gwl
 */
public final class AssertUtils {

    private AssertUtils() {}

    /** 对象不为 null，否则抛出 BusinessException */
    public static void notNull(Object obj, ErrorCode errorCode, Object... args) {
        if (obj == null) {
            throw new BusinessException(errorCode, args);
        }
    }

    /** 条件为 true，否则抛出 BusinessException */
    public static void isTrue(boolean condition, ErrorCode errorCode, Object... args) {
        if (!condition) {
            throw new BusinessException(errorCode, args);
        }
    }

    /** 条件为 false，否则抛出 BusinessException */
    public static void isFalse(boolean condition, ErrorCode errorCode, Object... args) {
        if (condition) {
            throw new BusinessException(errorCode, args);
        }
    }

    /** 字符串不为空，否则抛出 BusinessException */
    public static void hasText(String text, ErrorCode errorCode, Object... args) {
        if (text == null || text.isBlank()) {
            throw new BusinessException(errorCode, args);
        }
    }

    /** 对象为 null，否则抛出 BusinessException */
    public static void isNull(Object obj, ErrorCode errorCode, Object... args) {
        if (obj != null) {
            throw new BusinessException(errorCode, args);
        }
    }
}
