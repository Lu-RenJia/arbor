package com.arbor.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.arbor.common.AssertUtils;
import com.arbor.common.AuthException;
import com.arbor.common.BusinessException;
import com.arbor.common.ErrorCode;
import com.arbor.common.PageResult;
import com.arbor.common.TenantContext;
import com.arbor.dto.LoginDTO;
import com.arbor.dto.UserDTO;
import com.arbor.entity.Department;
import com.arbor.entity.Environment;
import com.arbor.entity.User;
import com.arbor.mapper.DepartmentMapper;
import com.arbor.mapper.EnvironmentMapper;
import com.arbor.mapper.UserMapper;
import com.arbor.mapper.UserRoleMapper;
import com.arbor.util.JwtUtil;
import com.arbor.vo.LoginVO;
import com.arbor.vo.UserVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 用户服务
 *
 * @author gwl
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserMapper userMapper;
    private final UserRoleMapper userRoleMapper;
    private final DepartmentMapper departmentMapper;
    private final EnvironmentMapper environmentMapper;
    private final RoleService roleService;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final com.arbor.security.TokenService tokenService;

    // ====== 认证相关 ======

    public LoginVO login(LoginDTO loginDTO) {
        User user = userMapper.selectByUsername(loginDTO.getUsername());
        if (user == null) {
            throw new AuthException(ErrorCode.LOGIN_FAILED);
        }
        if ("1".equals(user.getStatus())) {
            throw new AuthException(ErrorCode.ACCOUNT_DISABLED);
        }
        if (!passwordEncoder.matches(loginDTO.getPassword(), user.getPassword())) {
            throw new AuthException(ErrorCode.LOGIN_FAILED);
        }
        // 多角色支持：从 user_role 表查询用户的所有角色，合并权限
        List<String> roleCodes = userRoleMapper.selectRoleCodesByUserId(user.getId());
        if (roleCodes.isEmpty()) {
            roleCodes = List.of(user.getRole() != null ? user.getRole() : "USER");
        }
        java.util.Set<String> permissionSet = new java.util.LinkedHashSet<>();
        for (String rc : roleCodes) {
            if ("ADMIN".equals(rc)) {
                permissionSet.add("*:*:*");
                break;
            }
            permissionSet.addAll(roleService.getPermissionCodesByRoleCode(rc));
        }
        List<String> permissions = new java.util.ArrayList<>(permissionSet);
        String primaryRole = roleCodes.get(0);

        // Redis 模式：构建 LoginUser → 存 Redis → 返回 UUID token
        com.arbor.security.LoginUser loginUser = com.arbor.security.LoginUser.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .role(primaryRole)
                .permissions(permissions)
                .build();
        String token = tokenService.createToken(loginUser);
        log.info("登录成功: {}, userId={}, roles={}, perms={}", user.getUsername(), user.getId(), roleCodes, permissions.size());
        return LoginVO.builder()
                .id(user.getId())
                .token(token).username(user.getUsername())
                .nickname(user.getNickname()).role(primaryRole)
                .permissions(permissions).build();
    }

    public User getCurrentUser(String username) {
        return userMapper.selectByUsername(username);
    }

    /** 修改个人基本资料（仅昵称 + 性别） */
    @Transactional
    public void updateProfile(Long userId, String nickname, String sex) {
        userMapper.updateProfile(userId, nickname, sex);
        log.info("用户修改基本资料: userId={}, nickname={}, sex={}", userId, nickname, sex);
    }

    /** 修改密码（需验证旧密码） */
    @Transactional
    public void changePassword(Long userId, String oldPassword, String newPassword) {
        User user = userMapper.selectById(userId);
        AssertUtils.notNull(user, ErrorCode.USER_NOT_FOUND);
        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw new BusinessException(ErrorCode.OLD_PASSWORD_WRONG);
        }
        if (newPassword == null || newPassword.length() < 6) {
            throw new BusinessException(ErrorCode.PASSWORD_TOO_SHORT);
        }
        userMapper.updatePassword(userId, passwordEncoder.encode(newPassword));
        log.info("用户修改密码: userId={}, username={}", userId, user.getUsername());
    }

    /**
     * 解析用户权限（多角色合并），返回 (主角色, 权限列表)
     */
    public PermResult resolveUserPermissions(Long userId) {
        List<String> roleCodes = userRoleMapper.selectRoleCodesByUserId(userId);
        User user = userMapper.selectById(userId);
        if (roleCodes.isEmpty()) {
            roleCodes = List.of(user != null && user.getRole() != null ? user.getRole() : "USER");
        }
        java.util.Set<String> set = new java.util.LinkedHashSet<>();
        for (String rc : roleCodes) {
            if ("ADMIN".equals(rc)) { set.add("*:*:*"); break; }
            set.addAll(roleService.getPermissionCodesByRoleCode(rc));
        }
        String primaryRole = roleCodes.get(0);
        return new PermResult(primaryRole, new java.util.ArrayList<>(set));
    }

    public record PermResult(String role, List<String> permissions) {}

    // ====== 用户管理（管理员） ======

    public PageResult<UserVO> page(int pageNum, int pageSize) {
        IPage<User> page = new Page<>(pageNum, pageSize);
        IPage<User> result = userMapper.selectPage(page);
        Map<Long, Department> deptMap = buildDeptMap();
        List<UserVO> voList = result.getRecords().stream().map(u -> {
            String dn = u.getDepartmentId() != null && deptMap.containsKey(u.getDepartmentId())
                    ? deptMap.get(u.getDepartmentId()).getName() : null;
            return UserVO.fromEntity(u, dn);
        }).collect(Collectors.toList());
        return PageResult.of(pageNum, pageSize, result.getTotal(), voList);
    }

    public List<UserVO> listAll() {
        IPage<User> page = new Page<>(1, 10000);
        Map<Long, Department> deptMap = buildDeptMap();
        return userMapper.selectPage(page).getRecords().stream().map(u -> {
            String dn = u.getDepartmentId() != null && deptMap.containsKey(u.getDepartmentId())
                    ? deptMap.get(u.getDepartmentId()).getName() : null;
            return UserVO.fromEntity(u, dn);
        }).collect(Collectors.toList());
    }

    @Transactional
    public UserVO create(UserDTO dto) {
        AssertUtils.hasText(dto.getUsername(), ErrorCode.USERNAME_REQUIRED);
        User exist = userMapper.selectByUsername(dto.getUsername());
        AssertUtils.isNull(exist, ErrorCode.USERNAME_EXISTS);
        AssertUtils.hasText(dto.getPassword(), ErrorCode.PASSWORD_REQUIRED);

        User entity = new User();
        entity.setUsername(dto.getUsername());
        entity.setPassword(passwordEncoder.encode(dto.getPassword()));
        entity.setNickname(dto.getNickname() != null ? dto.getNickname() : dto.getUsername());
        entity.setRole(dto.getRole() != null ? dto.getRole() : "USER");
        entity.setDepartmentId(dto.getDepartmentId());
        entity.setStatus(dto.getStatus() != null ? dto.getStatus() : "0");
        userMapper.insert(entity);
        log.info("新增用户: {}", dto.getUsername());

        // 为新用户自动创建默认环境：需临时切换 TenantContext 为目标用户，
        // 否则租户插件会注入当前操作者（管理员）的 user_id
        Long currentUserId = TenantContext.getUserId();
        boolean currentAdmin = TenantContext.isAdmin();
        TenantContext.set(entity.getId(), false);
        try {
            Environment defaultEnv = new Environment();
            defaultEnv.setName("默认环境");
            defaultEnv.setCode("default");
            defaultEnv.setIsDefault(1);
            defaultEnv.setIsDeleted(0);
            defaultEnv.setSortOrder(0);
            environmentMapper.insert(defaultEnv);
            log.info("为用户 {} 创建默认环境 envId={}", dto.getUsername(), defaultEnv.getId());
        } finally {
            TenantContext.set(currentUserId, currentAdmin);
        }

        return UserVO.fromEntity(entity, null);
    }

    @Transactional
    public UserVO update(Long id, UserDTO dto) {
        User entity = userMapper.selectById(id);
        AssertUtils.notNull(entity, ErrorCode.USER_NOT_FOUND);
        entity.setNickname(dto.getNickname());
        entity.setRole(dto.getRole());
        entity.setDepartmentId(dto.getDepartmentId());
        entity.setStatus(dto.getStatus());
        userMapper.update(entity);
        log.info("修改用户: id={}, username={}", id, entity.getUsername());
        return UserVO.fromEntity(entity, null);
    }

    @Transactional
    public void resetPassword(Long id, String newPassword) {
        User entity = userMapper.selectById(id);
        AssertUtils.notNull(entity, ErrorCode.USER_NOT_FOUND);
        userMapper.updatePassword(id, passwordEncoder.encode(newPassword));
        log.info("重置密码: id={}, username={}", id, entity.getUsername());
    }

    @Transactional
    public void delete(Long id) {
        User entity = userMapper.selectById(id);
        AssertUtils.notNull(entity, ErrorCode.USER_NOT_FOUND);
        AssertUtils.isTrue(!"admin".equals(entity.getUsername()), ErrorCode.ADMIN_CANNOT_DELETE);
        userMapper.deleteById(id);
        log.info("删除用户: id={}, username={}", id, entity.getUsername());
    }

    @Transactional
    public void updateStatus(Long id, String status) {
        User entity = userMapper.selectById(id);
        AssertUtils.notNull(entity, ErrorCode.USER_NOT_FOUND);
        userMapper.updateStatus(id, status);
        log.info("用户状态变更: id={}, status={}", id, status);
    }

    /** 查询用户的角色ID列表 */
    public List<Long> getAuthRoleIds(Long userId) {
        return userRoleMapper.selectRoleIdsByUserId(userId);
    }

    /** 分配用户角色：先删后插 */
    @Transactional
    public void assignUserRoles(Long userId, List<Long> roleIds) {
        AssertUtils.notNull(userMapper.selectById(userId), ErrorCode.USER_NOT_FOUND);
        userRoleMapper.deleteByUserId(userId);
        if (roleIds != null && !roleIds.isEmpty()) {
            userRoleMapper.batchInsert(userId, roleIds);
        }
        log.info("用户角色分配: userId={}, roleIds={}", userId, roleIds);
        // 刷新该用户 Redis 权限缓存，立即生效无需重新登录
        refreshUserPermsInRedis(userId);
    }

    /** 重新查询用户权限并刷新 Redis 缓存 */
    private void refreshUserPermsInRedis(Long userId) {
        try {
            List<String> roleCodes = userRoleMapper.selectRoleCodesByUserId(userId);
            if (roleCodes.isEmpty()) {
                User u = userMapper.selectById(userId);
                roleCodes = List.of(u != null && u.getRole() != null ? u.getRole() : "USER");
            }
            java.util.Set<String> set = new java.util.LinkedHashSet<>();
            for (String rc : roleCodes) {
                if ("ADMIN".equals(rc)) { set.add("*:*:*"); break; }
                set.addAll(roleService.getPermissionCodesByRoleCode(rc));
            }
            tokenService.refreshUserPerms(userId, new java.util.ArrayList<>(set));
        } catch (Exception e) {
            log.warn("刷新用户权限缓存失败: userId={}", userId, e);
        }
    }

    private Map<Long, Department> buildDeptMap() {
        return departmentMapper.selectAll().stream()
                .collect(Collectors.toMap(Department::getId, Function.identity()));
    }
}
