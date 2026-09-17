package com.arbor.common;

/**
 * 鉴权异常（未登录、token 无效等）
 * <p>
 * 全局异常处理器返回 401 给前端。
 * </p>
 *
 * @author gwl
 */
public class AuthException extends BaseException {

    public AuthException(ErrorCode errorCode, Object... args) {
        super(errorCode, args);
    }
}
