package com.arbor.config;

import com.arbor.common.Audit;
import com.arbor.entity.AuditLog;
import com.arbor.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * 操作审计 AOP 切面 — 拦截 @Audit 注解，成功/失败均落 audit_log。
 * <p>
 * 审计写入失败不影响业务（AuditLogService.recordSafely 内部吞异常）。
 * </p>
 *
 * @author gwl
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class AuditLogAspect {

    private final AuditLogService auditLogService;

    /** error_msg 最大长度 */
    private static final int ERROR_MSG_MAX_LENGTH = 500;

    @Around("@annotation(audit)")
    public Object around(ProceedingJoinPoint joinPoint, Audit audit) throws Throwable {
        try {
            Object result = joinPoint.proceed();
            record(joinPoint, audit, true, null);
            return result;
        } catch (Throwable t) {
            record(joinPoint, audit, false, t);
            throw t;
        }
    }

    private void record(ProceedingJoinPoint joinPoint, Audit audit, boolean success, Throwable error) {
        try {
            AuditLog auditLog = new AuditLog();
            auditLog.setUsername(currentUsername());
            auditLog.setModuleName(audit.module());
            auditLog.setAction(audit.action());
            auditLog.setDescription(audit.description().isEmpty()
                    ? audit.module() + audit.action()
                    : audit.description());
            auditLog.setDetail(auditLogService.buildDetail(joinPoint.getArgs()));
            auditLog.setSuccess(success ? 1 : 0);
            if (error != null) {
                String msg = String.valueOf(error.getMessage());
                auditLog.setErrorMsg(msg.length() > ERROR_MSG_MAX_LENGTH
                        ? msg.substring(0, ERROR_MSG_MAX_LENGTH) : msg);
            }
            auditLogService.fillRequestContext(auditLog);
            auditLogService.recordSafely(auditLog);
        } catch (Exception e) {
            log.warn("审计切面构建日志失败（不影响业务）: {}", e.getMessage());
        }
    }

    private String currentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getName())) {
            return auth.getName();
        }
        return "unknown";
    }
}
