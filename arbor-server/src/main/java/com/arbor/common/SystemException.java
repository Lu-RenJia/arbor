package com.arbor.common;

/**
 * 系统级异常（数据库连接失败等不可预期错误）
 * <p>
 * 全局异常处理器以 error 级别记录并告警，返回 500 给前端（不暴露堆栈）。
 * </p>
 *
 * @author gwl
 */
public class SystemException extends BaseException {

    public SystemException(ErrorCode errorCode, Object... args) {
        super(errorCode, args);
    }

    public SystemException(ErrorCode errorCode, Throwable cause, Object... args) {
        super(errorCode, cause, args);
    }
}
