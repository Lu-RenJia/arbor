package com.arbor.controller;

import com.arbor.common.Audit;
import com.arbor.common.Result;
import com.arbor.dto.LoginDTO;
import com.arbor.entity.AuditLog;
import com.arbor.entity.User;
import com.arbor.security.TokenService;
import com.arbor.service.AuditLogService;
import com.arbor.service.RoleService;
import com.arbor.service.UserService;
import com.arbor.vo.LoginVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 认证控制器
 *
 * @author gwl
 */
@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final RoleService roleService;
    private final TokenService tokenService;
    private final AuditLogService auditLogService;

    private static final String TOKEN_PREFIX = "Bearer ";
    private static final int TOKEN_PREFIX_LENGTH = 7;

    /**
     * 用户登录（成功/失败均写审计）
     */
    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginDTO loginDTO, HttpServletRequest request) {
        LoginVO loginVO;
        try {
            loginVO = userService.login(loginDTO);
        } catch (RuntimeException e) {
            recordAuth(loginDTO.getUsername(), "LOGIN_FAILED", "登录失败", 0, e.getMessage(), request);
            throw e;
        }
        recordAuth(loginDTO.getUsername(), "LOGIN", "登录成功", 1, null, request);
        return Result.ok("登录成功", loginVO);
    }

    /**
     * 用户登出 — 删除 Redis 中的 token
     */
    @PostMapping("/logout")
    public Result<String> logout(HttpServletRequest request) {
        String token = extractToken(request);
        if (token != null) {
            tokenService.delLoginUser(token);
        }
        recordAuth(currentUserOrNull(), "LOGOUT", "登出", 1, null, request);
        return Result.ok("已登出");
    }

    /** 写入认证类审计记录（登录场景无 SecurityContext，由调用方显式传入用户名） */
    private void recordAuth(String username, String action, String description,
                            int success, String errorMsg, HttpServletRequest request) {
        AuditLog auditLog = new AuditLog();
        auditLog.setUsername(username != null ? username : "unknown");
        auditLog.setModuleName("认证");
        auditLog.setAction(action);
        auditLog.setDescription(description);
        auditLog.setSuccess(success);
        auditLog.setErrorMsg(errorMsg);
        auditLog.setMethod(request.getMethod());
        auditLog.setUri(request.getRequestURI());
        auditLog.setIp(AuditLogService.resolveIp(request));
        auditLogService.recordSafely(auditLog);
    }

    /** 从 SecurityContext 取当前用户名（未认证返回 null） */
    private String currentUserOrNull() {
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getName())) {
            return auth.getName();
        }
        return null;
    }

    /**
     * 获取当前用户信息（从 DB 查最新角色权限，用于前端刷新菜单）
     */
    @GetMapping("/info")
    public Result<LoginVO> getUserInfo() {
        String username = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication().getName();
        var user = userService.getCurrentUser(username);
        if (user == null) {
            return Result.unauthorized("用户不存在");
        }
        // 使用与 login 一致的多角色权限解析
        var permResult = userService.resolveUserPermissions(user.getId());
        LoginVO loginVO = LoginVO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .role(permResult.role())
                .permissions(permResult.permissions())
                .build();
        return Result.ok(loginVO);
    }

    private String extractToken(HttpServletRequest request) {
        String bearer = request.getHeader("Authorization");
        if (StringUtils.hasText(bearer) && bearer.startsWith(TOKEN_PREFIX)) {
            return bearer.substring(TOKEN_PREFIX_LENGTH);
        }
        return null;
    }

    /** 获取当前登录用户ID */
    private Long currentUserId() {
        String username = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication().getName();
        User user = userService.getCurrentUser(username);
        return user != null ? user.getId() : null;
    }

    /**
     * 获取当前用户基本资料（个人中心用）
     */
    @GetMapping("/profile")
    public Result<java.util.Map<String, Object>> getProfile() {
        String username = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication().getName();
        User user = userService.getCurrentUser(username);
        if (user == null) return Result.unauthorized("用户不存在");
        var map = new java.util.LinkedHashMap<String, Object>();
        map.put("username", user.getUsername());
        map.put("nickname", user.getNickname());
        map.put("sex", user.getSex());
        map.put("phone", user.getPhone());
        map.put("email", user.getEmail());
        map.put("createTime", user.getCreateTime());
        return Result.ok(map);
    }

    /**
     * 修改基本资料（仅昵称 + 性别）
     */
    @PutMapping("/profile")
    public Result<String> updateProfile(@RequestBody java.util.Map<String, String> body) {
        Long userId = currentUserId();
        if (userId == null) return Result.unauthorized("用户不存在");
        String nickname = body.get("nickname");
        String sex = body.get("sex");
        userService.updateProfile(userId, nickname, sex);
        return Result.ok("修改成功");
    }

    /**
     * 修改密码（需验证旧密码，敏感操作写审计）
     */
    @Audit(module = "认证", action = "UPDATE_PASSWORD", description = "修改密码")
    @PutMapping("/password")
    public Result<String> changePassword(@RequestBody java.util.Map<String, String> body) {
        Long userId = currentUserId();
        if (userId == null) return Result.unauthorized("用户不存在");
        String oldPassword = body.get("oldPassword");
        String newPassword = body.get("newPassword");
        userService.changePassword(userId, oldPassword, newPassword);
        return Result.ok("密码修改成功");
    }
}
