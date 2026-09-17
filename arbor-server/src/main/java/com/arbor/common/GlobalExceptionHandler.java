package com.arbor.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.text.MessageFormat;
import java.util.stream.Collectors;

/**
 * 全局异常处理器 — 分类兜底
 *
 * @author gwl
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 业务异常 — warn 级别（预期内，不需告警） */
    @ExceptionHandler(BusinessException.class)
    public Result<?> handleBusiness(BusinessException e) {
        log.warn("业务异常: code={}, msg={}", e.getCode(), formatMessage(e));
        return Result.fail(e.getCode(), formatMessage(e));
    }

    /** 鉴权异常 — 返回 401 */
    @ExceptionHandler(AuthException.class)
    public Result<?> handleAuth(AuthException e) {
        log.warn("鉴权异常: code={}, msg={}", e.getCode(), formatMessage(e));
        return Result.fail(Result.CODE_UNAUTHORIZED, formatMessage(e));
    }

    /** 系统异常 — error 级别（需要关注+告警） */
    @ExceptionHandler(SystemException.class)
    public Result<?> handleSystem(SystemException e) {
        log.error("系统异常: code={}, msg={}", e.getCode(), formatMessage(e), e);
        return Result.fail(e.getCode(), formatMessage(e));
    }

    /** 参数校验异常（Spring Validation） */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<?> handleValidation(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage).collect(Collectors.joining(", "));
        log.warn("参数校验失败: {}", msg);
        return Result.badRequest(msg);
    }

    /** 绑定异常 */
    @ExceptionHandler(BindException.class)
    public Result<?> handleBind(BindException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage).collect(Collectors.joining(", "));
        log.warn("参数绑定失败: {}", msg);
        return Result.badRequest(msg);
    }

    /** 兜底：未预期的运行时异常 — error 级别，不暴露堆栈给前端 */
    @ExceptionHandler(Exception.class)
    public Result<?> handleUnknown(Exception e) {
        log.error("未预期的系统异常，需要关注", e);
        return Result.fail(ErrorCode.SYSTEM_ERROR.getCode(), ErrorCode.SYSTEM_ERROR.getMessage());
    }

    private String formatMessage(BaseException e) {
        String template = e.getMessage();
        Object[] args = e.getArgs();
        if (args == null || args.length == 0) return template;
        try {
            return MessageFormat.format(template, args);
        } catch (Exception ex) {
            return template;
        }
    }
}
