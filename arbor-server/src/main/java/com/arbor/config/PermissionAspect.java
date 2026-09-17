package com.arbor.config;

import com.arbor.common.AuthException;
import com.arbor.common.ErrorCode;
import com.arbor.common.RequiresPermission;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * 权限校验 AOP 切面
 *
 * @author gwl
 */
@Aspect
@Component
public class PermissionAspect {

    @Around("@annotation(requiresPermission)")
    public Object checkPermission(ProceedingJoinPoint joinPoint, RequiresPermission requiresPermission) throws Throwable {
        String perm = requiresPermission.value();
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth == null || !auth.isAuthenticated()) {
            throw new AuthException(ErrorCode.UNAUTHORIZED);
        }

        Set<String> authorities = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());

        // 超级管理员拥有所有权限
        if (authorities.contains("*:*:*") || authorities.contains("ROLE_ADMIN")) {
            return joinPoint.proceed();
        }

        boolean hasPerm = switch (requiresPermission.logical()) {
            case ANY -> hasAnyPerm(authorities, perm);
            case ALL -> authorities.contains(perm);
        };

        if (!hasPerm) {
            String method = ((MethodSignature) joinPoint.getSignature()).getMethod().getName();
            throw new AuthException(ErrorCode.FORBIDDEN);
        }

        return joinPoint.proceed();
    }

    private boolean hasAnyPerm(Set<String> authorities, String perm) {
        for (String p : perm.split(",")) {
            if (authorities.contains(p.trim())) {
                return true;
            }
        }
        return false;
    }
}
