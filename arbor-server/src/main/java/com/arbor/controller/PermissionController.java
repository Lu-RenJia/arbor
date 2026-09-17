package com.arbor.controller;

import com.arbor.common.Result;
import com.arbor.entity.Permission;
import com.arbor.mapper.PermissionMapper;
import com.arbor.vo.PermissionVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 权限列表控制器（只读）
 *
 * @author gwl
 */
@RestController
@RequestMapping("/api/permission")
@RequiredArgsConstructor
public class PermissionController {

    private final PermissionMapper mapper;

    /** 全部权限列表 */
    @GetMapping("/list")
    public Result<List<PermissionVO>> list() {
        List<Permission> list = mapper.selectAll();
        return Result.ok(list.stream().map(PermissionVO::fromEntity).collect(Collectors.toList()));
    }
}
