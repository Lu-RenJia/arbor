package com.arbor.controller;

import com.arbor.common.Result;
import com.arbor.dto.FlowVariableDTO;
import com.arbor.service.FlowVariableService;
import com.arbor.vo.FlowVariableVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 流程变量池控制器
 *
 * @author gwl
 */
@Slf4j
@RestController
@RequestMapping("/api/flow-variable")
@RequiredArgsConstructor
public class FlowVariableController {

    private final FlowVariableService service;

    /** 列表 */
    @GetMapping("/list/{flowId}")
    public Result<List<FlowVariableVO>> list(@PathVariable Long flowId) {
        return Result.ok(service.listByFlowId(flowId));
    }

    /** 新增 */
    @PostMapping("/create/{flowId}")
    public Result<FlowVariableVO> create(@PathVariable Long flowId,
                                         @Valid @RequestBody FlowVariableDTO dto) {
        dto.setFlowId(flowId);
        return Result.ok("添加成功", service.create(dto));
    }

    /** 修改 */
    @PutMapping("/update/{flowId}/{id}")
    public Result<FlowVariableVO> update(@PathVariable Long flowId,
                                         @PathVariable Long id,
                                         @Valid @RequestBody FlowVariableDTO dto) {
        dto.setFlowId(flowId);
        return Result.ok("修改成功", service.update(id, dto));
    }

    /** 删除 */
    @DeleteMapping("/delete/{id}")
    public Result<String> delete(@PathVariable Long id) {
        service.delete(id);
        return Result.ok("删除成功");
    }

    /** 批量删除 */
    @PostMapping("/batch-delete/{flowId}")
    public Result<String> deleteBatch(@PathVariable Long flowId, @RequestBody List<Long> ids) {
        service.deleteBatch(ids);
        return Result.ok("删除成功");
    }
}
