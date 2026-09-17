package com.arbor.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.arbor.common.AssertUtils;
import com.arbor.common.BusinessException;
import com.arbor.common.ErrorCode;
import com.arbor.common.PageResult;
import com.arbor.dto.DepartmentDTO;
import com.arbor.entity.Department;
import com.arbor.mapper.DepartmentMapper;
import com.arbor.vo.DepartmentVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 部门管理服务
 *
 * @author gwl
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DepartmentService {

    private final DepartmentMapper mapper;

    public PageResult<DepartmentVO> page(int pageNum, int pageSize) {
        IPage<Department> page = new Page<>(pageNum, pageSize);
        IPage<Department> result = mapper.selectPage(page);
        Map<Long, Department> allMap = mapper.selectAll().stream()
                .collect(Collectors.toMap(Department::getId, Function.identity()));
        List<DepartmentVO> voList = result.getRecords().stream().map(d -> {
            String parentName = d.getParentId() != null && d.getParentId() > 0 && allMap.containsKey(d.getParentId())
                    ? allMap.get(d.getParentId()).getName() : null;
            return DepartmentVO.fromEntity(d, parentName);
        }).collect(Collectors.toList());
        return PageResult.of(pageNum, pageSize, result.getTotal(), voList);
    }

    public List<DepartmentVO> listAll() {
        Map<Long, Department> allMap = mapper.selectAll().stream()
                .collect(Collectors.toMap(Department::getId, Function.identity()));
        return mapper.selectAll().stream().map(d -> {
            String parentName = d.getParentId() != null && d.getParentId() > 0 && allMap.containsKey(d.getParentId())
                    ? allMap.get(d.getParentId()).getName() : null;
            return DepartmentVO.fromEntity(d, parentName);
        }).collect(Collectors.toList());
    }

    /** 部门树（下拉选择用） */
    public List<DepartmentVO> treeselect() {
        List<Department> all = mapper.selectAll();
        Map<Long, DepartmentVO> map = all.stream()
                .collect(Collectors.toMap(Department::getId, d -> DepartmentVO.fromEntity(d, null)));
        List<DepartmentVO> tree = new ArrayList<>();
        for (Department d : all) {
            DepartmentVO vo = map.get(d.getId());
            if (d.getParentId() == null || d.getParentId() == 0) {
                tree.add(vo);
            } else {
                DepartmentVO parent = map.get(d.getParentId());
                if (parent != null) {
                    if (parent.getChildren() == null) parent.setChildren(new ArrayList<>());
                    parent.getChildren().add(vo);
                }
            }
        }
        return tree;
    }

    @Transactional
    public DepartmentVO create(DepartmentDTO dto) {
        Department entity = new Department();
        entity.setName(dto.getName());
        entity.setParentId(dto.getParentId() != null ? dto.getParentId() : 0L);
        entity.setRemark(dto.getRemark());
        entity.setLeader(dto.getLeader());
        entity.setPhone(dto.getPhone());
        entity.setEmail(dto.getEmail());
        entity.setSortOrder(dto.getSortOrder() != null ? dto.getSortOrder() : 0);
        entity.setStatus(dto.getStatus() != null ? dto.getStatus() : "0");
        entity.setAncestors(buildAncestors(entity.getParentId()));
        mapper.insert(entity);
        log.info("新增部门: {}", dto.getName());
        return DepartmentVO.fromEntity(entity, null);
    }

    @Transactional
    public DepartmentVO update(Long id, DepartmentDTO dto) {
        Department entity = mapper.selectById(id);
        AssertUtils.notNull(entity, ErrorCode.DEPT_NOT_FOUND);
        entity.setName(dto.getName());
        entity.setParentId(dto.getParentId() != null ? dto.getParentId() : 0L);
        entity.setRemark(dto.getRemark());
        entity.setLeader(dto.getLeader());
        entity.setPhone(dto.getPhone());
        entity.setEmail(dto.getEmail());
        entity.setSortOrder(dto.getSortOrder() != null ? dto.getSortOrder() : 0);
        entity.setStatus(dto.getStatus() != null ? dto.getStatus() : "0");
        entity.setAncestors(buildAncestors(entity.getParentId()));
        mapper.update(entity);
        log.info("修改部门: id={}, name={}", id, dto.getName());
        return DepartmentVO.fromEntity(entity, null);
    }

    private String buildAncestors(Long parentId) {
        if (parentId == null || parentId == 0) return "0";
        Department parent = mapper.selectById(parentId);
        if (parent == null) return "0";
        String parentAncestors = parent.getAncestors() != null ? parent.getAncestors() : "0";
        return parentAncestors + "," + parentId;
    }

    @Transactional
    public void delete(Long id) {
        AssertUtils.notNull(mapper.selectById(id), ErrorCode.DEPT_NOT_FOUND);
        mapper.deleteById(id);
        log.info("删除部门: id={}", id);
    }
}
