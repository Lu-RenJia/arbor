package com.arbor.controller;

import com.arbor.common.Result;
import com.arbor.dto.FieldMappingDTO;
import com.arbor.service.FieldMappingService;
import com.arbor.vo.FieldMappingVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 字段映射控制器 — 流程结果展示字段中英文映射 + 字典值替换
 *
 * @author gwl
 */
@RestController
@RequestMapping("/api/field-mapping")
@RequiredArgsConstructor
public class FieldMappingController {

    private final FieldMappingService service;

    /** 列表 */
    @GetMapping("/list/{flowId}")
    public Result<List<FieldMappingVO>> list(@PathVariable Long flowId) {
        return Result.ok(service.listByFlowId(flowId));
    }

    /** 新增 */
    @PostMapping("/create/{flowId}")
    public Result<FieldMappingVO> create(@PathVariable Long flowId,
                                         @Valid @RequestBody FieldMappingDTO dto) {
        dto.setFlowId(flowId);
        return Result.ok("添加成功", service.create(dto));
    }

    /** 修改 */
    @PutMapping("/update/{flowId}/{id}")
    public Result<FieldMappingVO> update(@PathVariable Long flowId,
                                         @PathVariable Long id,
                                         @Valid @RequestBody FieldMappingDTO dto) {
        dto.setFlowId(flowId);
        return Result.ok("修改成功", service.update(id, dto));
    }

    /** 删除 */
    @DeleteMapping("/delete/{flowId}/{id}")
    public Result<String> delete(@PathVariable Long flowId,
                                 @PathVariable Long id) {
        service.delete(id);
        return Result.ok("删除成功");
    }

    /** 批量保存（按 fieldName 更新已有或新增） */
    @PostMapping("/batch/{flowId}")
    public Result<List<FieldMappingVO>> batchSave(@PathVariable Long flowId,
                                                   @RequestBody List<FieldMappingDTO> list) {
        return Result.ok("保存成功", service.batchSave(flowId, list));
    }

    /** 对结果数据应用字段映射 */
    @PostMapping("/apply/{flowId}")
    public Result<Map<String, Object>> applyMapping(@PathVariable Long flowId,
                                                     @RequestBody Object data) {
        return Result.ok(service.applyMapping(data, flowId));
    }
}
