package com.arbor.controller;

import com.arbor.common.Result;
import com.arbor.dto.ToolClassDTO;
import com.arbor.service.ToolClassService;
import com.arbor.vo.ToolClassVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 工具类控制器
 * <p>
 * 全局共享的 Groovy 工具类管理：仅 admin 可创建/修改/删除，所有用户可查看和使用。
 * </p>
 *
 * @author gwl
 */
@Slf4j
@RestController
@RequestMapping("/api/tool-class")
@RequiredArgsConstructor
public class ToolClassController {

    private final ToolClassService service;

    /** 查询所有工具类列表（所有用户可调用） */
    @GetMapping("/list")
    public Result<List<ToolClassVO>> list() {
        return Result.ok(service.listTools());
    }

    /** 查看工具类详情（所有用户可调用） */
    @GetMapping("/{id}")
    public Result<ToolClassVO> get(@PathVariable Long id) {
        return Result.ok(service.getTool(id));
    }

    /** 创建工具类（仅 admin） */
    @PostMapping
    public Result<ToolClassVO> create(@Valid @RequestBody ToolClassDTO dto) {
        ToolClassVO vo = service.createTool(dto);
        // 检查数量是否超过阈值，附带警告信息
        int count = service.getToolCount();
        int threshold = service.getWarningThreshold();
        if (count > threshold) {
            return Result.ok("创建成功（提示：当前工具类数量已达 " + count + " 个，超过建议上限 "
                    + threshold + " 个，请关注 Metaspace 使用情况）", vo);
        }
        return Result.ok("创建成功", vo);
    }

    /** 更新工具类（仅 admin） */
    @PutMapping("/{id}")
    public Result<ToolClassVO> update(@PathVariable Long id, @Valid @RequestBody ToolClassDTO dto) {
        return Result.ok("修改成功", service.updateTool(id, dto));
    }

    /** 删除工具类（仅 admin） */
    @DeleteMapping("/{id}")
    public Result<String> delete(@PathVariable Long id) {
        service.deleteTool(id);
        return Result.ok("删除成功");
    }
}
