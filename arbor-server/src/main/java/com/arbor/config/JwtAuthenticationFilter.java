package com.arbor.config;

import com.arbor.common.TenantContext;
import com.arbor.security.LoginUser;
import com.arbor.security.TokenService;
import com.arbor.util.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * JWT 认证过滤器 — 支持纯 JWT / Redis Token 双模式
 *
 * @author gwl
 */
@Slf4j
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final TokenService tokenService;
    private final boolean skipAuth;

    /** HTTP 认证头名称 */
    private static final String HEADER_AUTHORIZATION = "Authorization";
    /** Bearer Token 前缀 */
    private static final String TOKEN_PREFIX = "Bearer ";
    /** Token 前缀长度 */
    private static final int TOKEN_PREFIX_LENGTH = 7;

    public JwtAuthenticationFilter(JwtUtil jwtUtil,
                                   TokenService tokenService,
                                   @Value("${arbor.auth.skip:false}") boolean skipAuth) {
        this.jwtUtil = jwtUtil;
        this.tokenService = tokenService;
        this.skipAuth = skipAuth;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {
        try {
            if (skipAuth) {
                setContext("admin", "ADMIN", List.of(), 1L, true);
            } else {
                String token = extractToken(request);
                if (!StringUtils.hasText(token)) {
                    filterChain.doFilter(request, response);
                    return;
                }

                // 优先从 Redis 查 LoginUser
                LoginUser loginUser = tokenService.getLoginUser(token);
                if (loginUser != null) {
                    // Redis 模式：从 LoginUser 获取所有信息，无需查 DB
                    tokenService.verifyToken(loginUser);
                    setContext(loginUser.getUsername(), loginUser.getRole(),
                            loginUser.getPermissions() != null ? loginUser.getPermissions() : List.of(),
                            loginUser.getUserId(), "ADMIN".equals(loginUser.getRole()));
                    log.debug("Redis认证成功: username={}, userId={}", loginUser.getUsername(), loginUser.getUserId());
                } else {
                    // 回退 JWT 模式：从 JWT 解析用户信息（兼容旧 token）
                    if (jwtUtil.validateToken(token)) {
                        String username = jwtUtil.getUsernameFromToken(token);
                        String role = jwtUtil.getRoleFromToken(token);
                        List<String> permissions = jwtUtil.getPermissionsFromToken(token);
                        boolean isAdmin = "ADMIN".equals(role);
                        // 旧 JWT 不含 userId，暂用 0
                        setContext(username, role, permissions != null ? permissions : List.of(), 0L, isAdmin);
                        log.debug("JWT回退认证成功: username={}", username);
                    }
                }
            }
            filterChain.doFilter(request, response);
        } finally {
            // 兜底清理：Tomcat 线程池复用下，残留的 ThreadLocal/SecurityContext 会导致下个请求串租户，
            // 尤其是无 token 的匿名请求不会走 setContext，更要依赖这里清理上一请求的残留
            TenantContext.clear();
            SecurityContextHolder.clearContext();
        }
    }

    private void setContext(String username, String role, List<String> permissions, Long userId, boolean isAdmin) {
        String roleAuthority = "ROLE_" + role;
        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority(roleAuthority));
        for (String perm : permissions) {
            authorities.add(new SimpleGrantedAuthority(perm));
        }
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(username, null, authorities);
        SecurityContextHolder.getContext().setAuthentication(authentication);
        TenantContext.set(userId, isAdmin);
    }

    /**
     * 从请求头中提取 Token (Bearer xxx)
     */
    private String extractToken(HttpServletRequest request) {
        String bearerToken = request.getHeader(HEADER_AUTHORIZATION);
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith(TOKEN_PREFIX)) {
            return bearerToken.substring(TOKEN_PREFIX_LENGTH);
        }
        return null;
    }
}
