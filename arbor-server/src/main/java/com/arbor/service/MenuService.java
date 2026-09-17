package com.arbor.service;

import com.arbor.common.AssertUtils;
import com.arbor.common.BusinessException;
import com.arbor.common.ErrorCode;
import com.arbor.dto.MenuDTO;
import com.arbor.entity.Menu;
import com.arbor.mapper.MenuMapper;
import com.arbor.mapper.PermissionMapper;
import com.arbor.vo.MenuVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 菜单管理服务
 *
 * @author gwl
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MenuService {

    private final MenuMapper mapper;
    private final PermissionMapper permissionMapper;

    /** 全部菜单列表（扁平） */
    public List<MenuVO> listAll() {
        return mapper.selectAll().stream().map(MenuVO::fromEntity).collect(Collectors.toList());
    }

    /** 菜单树（管理端，全部） */
    public List<MenuVO> getMenuTree() {
        List<Menu> all = mapper.selectAll();
        return buildTree(all);
    }

    /** treeselect：下拉树用，只含目录和菜单（不含按钮 F） */
    public List<MenuVO> treeselect() {
        List<Menu> all = mapper.selectAll();
        // 过滤掉按钮类型
        List<Menu> filtered = all.stream()
                .filter(m -> !"F".equals(m.getMenuType()))
                .collect(Collectors.toList());
        return buildTree(filtered);
    }

    /** 按角色编码查询可见菜单树 */
    public List<MenuVO> getMenuTreeByRoleCode(String roleCode) {
        List<String> permCodes = permissionMapper.selectCodesByRoleCode(roleCode);
        if (permCodes.isEmpty()) {
            // 该角色无任何权限 → 返回空列表
            return Collections.emptyList();
        }
        List<Menu> menus = mapper.selectByPermissionCodes(permCodes);
        List<MenuVO> tree = buildTree(menus);
        // 过滤掉没有子节点的纯目录（permission_code=NULL 的父菜单）
        return filterEmptyDirs(tree);
    }

    /** 递归过滤：移除 permissionCode 为空且没有子节点的目录节点 */
    private List<MenuVO> filterEmptyDirs(List<MenuVO> nodes) {
        return nodes.stream()
                .filter(node -> {
                    // 先递归过滤子节点
                    if (!node.getChildren().isEmpty()) {
                        node.setChildren(filterEmptyDirs(node.getChildren()));
                    }
                    // 保留：有权限码的页面节点，或有子节点的目录节点
                    return node.getPermissionCode() != null || !node.getChildren().isEmpty();
                })
                .collect(Collectors.toList());
    }

    /** 构建树形结构 */
    private List<MenuVO> buildTree(List<Menu> all) {
        Map<Long, MenuVO> voMap = all.stream()
                .map(MenuVO::fromEntity)
                .collect(Collectors.toMap(MenuVO::getId, v -> v, (a, b) -> a, LinkedHashMap::new));

        List<MenuVO> roots = new ArrayList<>();
        for (MenuVO vo : voMap.values()) {
            if (vo.getParentId() == null || vo.getParentId() == 0) {
                roots.add(vo);
            } else {
                MenuVO parent = voMap.get(vo.getParentId());
                if (parent != null) {
                    parent.getChildren().add(vo);
                }
            }
        }
        return roots;
    }

    /** 按ID查询 */
    public MenuVO getById(Long id) {
        Menu entity = mapper.selectById(id);
        AssertUtils.notNull(entity, ErrorCode.MENU_NOT_FOUND);
        return MenuVO.fromEntity(entity);
    }

    /** 新增 */
    @Transactional
    public MenuVO create(MenuDTO dto) {
        Menu entity = new Menu();
        entity.setParentId(dto.getParentId() != null ? dto.getParentId() : 0L);
        entity.setName(dto.getName());
        entity.setPath(dto.getPath());
        entity.setComponent(dto.getComponent());
        entity.setQuery(dto.getQuery());
        entity.setIsFrame(dto.getIsFrame() != null ? dto.getIsFrame() : "0");
        entity.setRouteName(dto.getRouteName());
        entity.setMenuType(dto.getMenuType() != null ? dto.getMenuType() : "M");
        entity.setIsCache(dto.getIsCache() != null ? dto.getIsCache() : "0");
        entity.setIcon(dto.getIcon());
        entity.setPermissionCode(dto.getPermissionCode());
        entity.setSortOrder(dto.getSortOrder() != null ? dto.getSortOrder() : 0);
        entity.setVisible(dto.getVisible() != null ? dto.getVisible() : 1);
        entity.setStatus(dto.getStatus() != null ? dto.getStatus() : "0");
        mapper.insert(entity);
        log.info("新增菜单: name={}, type={}, path={}", dto.getName(), entity.getMenuType(), dto.getPath());
        return MenuVO.fromEntity(entity);
    }

    /** 修改 */
    @Transactional
    public MenuVO update(Long id, MenuDTO dto) {
        Menu entity = mapper.selectById(id);
        AssertUtils.notNull(entity, ErrorCode.MENU_NOT_FOUND);
        entity.setParentId(dto.getParentId() != null ? dto.getParentId() : 0L);
        entity.setName(dto.getName());
        entity.setPath(dto.getPath());
        entity.setComponent(dto.getComponent());
        entity.setQuery(dto.getQuery());
        entity.setIsFrame(dto.getIsFrame() != null ? dto.getIsFrame() : "0");
        entity.setRouteName(dto.getRouteName());
        entity.setMenuType(dto.getMenuType() != null ? dto.getMenuType() : "M");
        entity.setIsCache(dto.getIsCache() != null ? dto.getIsCache() : "0");
        entity.setIcon(dto.getIcon());
        entity.setPermissionCode(dto.getPermissionCode());
        entity.setSortOrder(dto.getSortOrder() != null ? dto.getSortOrder() : 0);
        entity.setVisible(dto.getVisible() != null ? dto.getVisible() : 1);
        entity.setStatus(dto.getStatus() != null ? dto.getStatus() : "0");
        mapper.update(entity);
        log.info("修改菜单: id={}, name={}", id, dto.getName());
        return MenuVO.fromEntity(entity);
    }

    /** 删除（安全检查：有子菜单或有角色引用时禁止删除） */
    @Transactional
    public void delete(Long id) {
        Menu entity = mapper.selectById(id);
        AssertUtils.notNull(entity, ErrorCode.MENU_NOT_FOUND);

        if (mapper.countByParentId(id) > 0) {
            throw new BusinessException(ErrorCode.MENU_HAS_CHILDREN);
        }
        if (entity.getPermissionCode() != null && !entity.getPermissionCode().isBlank()
                && mapper.countRoleRefByPermissionCode(entity.getPermissionCode()) > 0) {
            throw new BusinessException(ErrorCode.MENU_IN_USE);
        }
        mapper.deleteById(id);
        log.info("删除菜单: id={}, name={}", id, entity.getName());
    }
}
