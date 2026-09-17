package com.arbor.common;

/**
 * 业务逻辑异常（可预期的校验失败、数据不存在等）
 *
 * @author gwl
 */
public class BusinessException extends BaseException {

    public BusinessException(ErrorCode errorCode, Object... args) {
        super(errorCode, args);
    }

    public BusinessException(ErrorCode errorCode, Throwable cause, Object... args) {
        super(errorCode, cause, args);
    }
}
