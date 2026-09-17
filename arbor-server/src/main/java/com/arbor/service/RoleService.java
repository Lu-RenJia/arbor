package com.arbor.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.arbor.common.AssertUtils;
import com.arbor.common.ErrorCode;
import com.arbor.common.PageResult;
import com.arbor.dto.RoleDTO;
import com.arbor.entity.Permission;
import com.arbor.entity.Role;
import com.arbor.mapper.PermissionMapper;
import com.arbor.mapper.RoleMapper;
import com.arbor.mapper.RolePermissionMapper;
import com.arbor.vo.PermissionVO;
import com.arbor.vo.RoleVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 角色管理服务
 *
 * @author gwl
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoleService {

    private final RoleMapper mapper;
    private final PermissionMapper permissionMapper;
    private final RolePermissionMapper rolePermissionMapper;
    private final com.arbor.mapper.UserRoleMapper userRoleMapper;
    private final com.arbor.security.TokenService tokenService;

    /** 分页查询 */
    public PageResult<RoleVO> page(int pageNum, int pageSize) {
        IPage<Role> page = new Page<>(pageNum, pageSize);
        IPage<Role> result = mapper.selectPage(page);
        List<RoleVO> voList = result.getRecords().stream().map(RoleVO::fromEntity).collect(Collectors.toList());
        return PageResult.of(pageNum, pageSize, result.getTotal(), voList);
    }

    /** 全部列表（下拉选择等） */
    public List<RoleVO> listAll() {
        return mapper.selectAll().stream().map(RoleVO::fromEntity).collect(Collectors.toList());
    }

    /** 角色详情（含权限） */
    public RoleVO getDetail(Long id) {
        Role entity = mapper.selectById(id);
        AssertUtils.notNull(entity, ErrorCode.ROLE_NOT_FOUND);
        RoleVO vo = RoleVO.fromEntity(entity);
        List<Permission> perms = permissionMapper.selectByRoleId(id);
        vo.setPermissions(perms.stream().map(PermissionVO::fromEntity).collect(Collectors.toList()));
        vo.setPermissionIds(perms.stream().map(Permission::getId).collect(Collectors.toList()));
        return vo;
    }

    /** 按编码查询 */
    public RoleVO getByCode(String code) {
        Role entity = mapper.selectByCode(code);
        return entity != null ? RoleVO.fromEntity(entity) : null;
    }

    /** 新增 */
    @Transactional
    public RoleVO create(RoleDTO dto) {
        Role exist = mapper.selectByCode(dto.getCode());
        AssertUtils.isNull(exist, ErrorCode.ROLE_CODE_EXISTS, dto.getCode());
        Role entity = new Role();
        entity.setCode(dto.getCode());
        entity.setRoleKey(dto.getRoleKey() != null ? dto.getRoleKey() : dto.getCode());
        entity.setName(dto.getName());
        entity.setRemark(dto.getRemark());
        entity.setRoleSort(dto.getRoleSort() != null ? dto.getRoleSort() : 0);
        entity.setStatus(dto.getStatus() != null ? dto.getStatus() : "0");
        mapper.insert(entity);
        log.info("新增角色: code={}, name={}", dto.getCode(), dto.getName());
        return RoleVO.fromEntity(entity);
    }

    /** 修改 */
    @Transactional
    public RoleVO update(Long id, RoleDTO dto) {
        Role entity = mapper.selectById(id);
        AssertUtils.notNull(entity, ErrorCode.ROLE_NOT_FOUND);
        // code 唯一校验（排除自身）
        if (!entity.getCode().equals(dto.getCode())) {
            Role codeExist = mapper.selectByCode(dto.getCode());
            AssertUtils.isNull(codeExist, ErrorCode.ROLE_CODE_EXISTS, dto.getCode());
        }
        entity.setCode(dto.getCode());
        entity.setRoleKey(dto.getRoleKey() != null ? dto.getRoleKey() : dto.getCode());
        entity.setName(dto.getName());
        entity.setRemark(dto.getRemark());
        entity.setRoleSort(dto.getRoleSort() != null ? dto.getRoleSort() : 0);
        entity.setStatus(dto.getStatus() != null ? dto.getStatus() : "0");
        mapper.update(entity);
        log.info("修改角色: id={}, code={}", id, dto.getCode());
        return RoleVO.fromEntity(entity);
    }

    /** 删除（默认角色禁止） */
    @Transactional
    public void delete(Long id) {
        Role entity = mapper.selectById(id);
        AssertUtils.notNull(entity, ErrorCode.ROLE_NOT_FOUND);
        AssertUtils.isTrue(!"ADMIN".equals(entity.getCode()) && !"USER".equals(entity.getCode()),
                ErrorCode.ROLE_CANNOT_DELETE);
        rolePermissionMapper.deleteByRoleId(id);
        mapper.deleteById(id);
        log.info("删除角色: id={}, code={}", id, entity.getCode());
    }

    /** 分配权限：先删后插 */
    @Transactional
    public void assignPermissions(Long roleId, List<Long> permissionIds) {
        AssertUtils.notNull(mapper.selectById(roleId), ErrorCode.ROLE_NOT_FOUND);
        rolePermissionMapper.deleteByRoleId(roleId);
        if (permissionIds != null && !permissionIds.isEmpty()) {
            rolePermissionMapper.batchInsert(roleId, permissionIds);
        }
        log.info("角色权限分配: roleId={}, count={}", roleId, permissionIds != null ? permissionIds.size() : 0);
        // 刷新拥有该角色的所有用户的权限缓存，立即生效无需重新登录
        refreshAffectedUsers(roleId);
    }

    private void refreshAffectedUsers(Long roleId) {
        try {
            List<Long> userIds = userRoleMapper.selectUserIdsByRoleId(roleId);
            String roleCode = null;
            for (Long uid : userIds) {
                if (roleCode == null) {
                    // 重新查该用户在当前角色下的权限码
                    List<String> codes = userRoleMapper.selectRoleCodesByUserId(uid);
                    tokenService.refreshUserPerms(uid, resolvePermissions(codes));
                    roleCode = codes.isEmpty() ? "" : codes.get(0); // 取第一个角色码做快速路径
                } else {
                    tokenService.refreshUserPerms(uid, resolvePermissions(
                            userRoleMapper.selectRoleCodesByUserId(uid)));
                }
            }
            if (!userIds.isEmpty()) {
                log.info("角色权限变更，已刷新 {} 个用户的缓存: roleId={}", userIds.size(), roleId);
            }
        } catch (Exception e) {
            log.warn("刷新角色关联用户权限失败: roleId={}", roleId, e);
        }
    }

    private List<String> resolvePermissions(List<String> roleCodes) {
        if (roleCodes.isEmpty()) return List.of();
        java.util.Set<String> set = new java.util.LinkedHashSet<>();
        for (String rc : roleCodes) {
            if ("ADMIN".equals(rc)) { set.add("*:*:*"); break; }
            set.addAll(getPermissionCodesByRoleCode(rc));
        }
        return new java.util.ArrayList<>(set);
    }

    /** 查询角色权限ID列表 */
    public List<Long> getPermissionIds(Long roleId) {
        return rolePermissionMapper.selectPermissionIdsByRoleId(roleId);
    }

    /** 查询角色权限码列表 */
    public List<String> getPermissionCodesByRoleCode(String roleCode) {
        List<String> codes = permissionMapper.selectCodesByRoleCode(roleCode);
        return codes != null ? codes : Collections.emptyList();
    }

    /** 保存数据权限范围 */
    @Transactional
    public void saveDataScope(Long roleId, String dataScope) {
        Role entity = mapper.selectById(roleId);
        AssertUtils.notNull(entity, ErrorCode.ROLE_NOT_FOUND);
        entity.setDataScope(dataScope);
        mapper.update(entity);
        log.info("角色数据权限: roleId={}, dataScope={}", roleId, dataScope);
    }
}
