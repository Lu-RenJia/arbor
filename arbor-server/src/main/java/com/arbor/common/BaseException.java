package com.arbor.common;

import lombok.Getter;

import java.text.MessageFormat;

/**
 * 业务异常抽象基类 — 全局异常处理器据此区分预期内异常和系统崩溃
 *
 * @author gwl
 */
@Getter
public abstract class BaseException extends RuntimeException {

    private final int code;
    private final Object[] args;

    protected BaseException(ErrorCode errorCode, Object... args) {
        super(format(errorCode.getMessage(), args));
        this.code = errorCode.getCode();
        this.args = args;
    }

    protected BaseException(ErrorCode errorCode, Throwable cause, Object... args) {
        super(format(errorCode.getMessage(), args), cause);
        this.code = errorCode.getCode();
        this.args = args;
    }

    private static String format(String template, Object... args) {
        if (args == null || args.length == 0) return template;
        try { return MessageFormat.format(template, args); } catch (Exception e) { return template; }
    }
}
