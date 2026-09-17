package com.arbor.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 统一响应格式
 *
 * @author gwl
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Result<T> {

    /** HTTP 成功状态码 */
    public static final int CODE_OK = 200;
    public static final int CODE_BAD_REQUEST = 400;
    public static final int CODE_UNAUTHORIZED = 401;
    public static final int CODE_FORBIDDEN = 403;
    public static final int CODE_NOT_FOUND = 404;
    public static final int CODE_ERROR = 500;

    private static final String MSG_SUCCESS = "操作成功";

    /** 业务状态码 */
    private Integer code;

    /** 提示信息 */
    private String message;

    /** 响应数据 */
    private T data;

    // ====== 成功 ======
    public static <T> Result<T> ok() { return new Result<>(CODE_OK, MSG_SUCCESS, null); }
    public static <T> Result<T> ok(T data) { return new Result<>(CODE_OK, MSG_SUCCESS, data); }
    public static <T> Result<T> ok(String message, T data) { return new Result<>(CODE_OK, message, data); }

    // ====== 失败 ======
    public static <T> Result<T> fail(int code, String message) { return new Result<>(code, message, null); }
    public static <T> Result<T> fail(String message) { return new Result<>(CODE_ERROR, message, null); }

    // ====== 常用状态码 ======
    public static <T> Result<T> badRequest(String message) { return new Result<>(CODE_BAD_REQUEST, message, null); }
    public static <T> Result<T> unauthorized(String message) { return new Result<>(CODE_UNAUTHORIZED, message, null); }
    public static <T> Result<T> notFound(String message) { return new Result<>(CODE_NOT_FOUND, message, null); }
}
