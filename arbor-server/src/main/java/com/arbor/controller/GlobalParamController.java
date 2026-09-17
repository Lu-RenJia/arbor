package com.arbor.controller;

import com.arbor.common.PageResult;
import com.arbor.common.Audit;
import com.arbor.common.Result;
import com.arbor.dto.GlobalParamDTO;
import com.arbor.service.GlobalParamService;
import com.arbor.vo.GlobalParamVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 全局参数控制器
 *
 * @author gwl
 */
@Slf4j
@RestController
@RequestMapping("/api/global-param")
@RequiredArgsConstructor
public class GlobalParamController {

    private final GlobalParamService service;

    /** 分页查询（按去重参数名） */
    @GetMapping("/page")
    public Result<PageResult<GlobalParamVO>> page(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String sortField,
            @RequestParam(required = false) String sortOrder) {
        return Result.ok(service.page(pageNum, pageSize, keyword, sortField, sortOrder));
    }

    /** 全部列表（参数定义） */
    @GetMapping("/list")
    public Result<List<GlobalParamVO>> list() {
        return Result.ok(service.listAll());
    }

    /** 获取所有参数Map（供引擎使用，自动感知当前环境） */
    @GetMapping("/map")
    public Result<Map<String, String>> paramMap() {
        return Result.ok(service.getAllParams());
    }

    /** 按参数名查询各环境的值（"参数管理"子列表） */
    @GetMapping("/values")
    public Result<List<GlobalParamVO>> listValues(@RequestParam String paramName) {
        return Result.ok(service.listValuesByParamName(paramName));
    }

    /** 保存某参数在某环境下的值 */
    @Audit(module = "全局参数", action = "UPDATE", description = "保存环境参数值")
    @PutMapping("/values/{paramId}/{envId}")
    public Result<String> saveValue(
            @PathVariable Long paramId,
            @PathVariable Long envId,
            @RequestBody Map<String, String> body) {
        service.saveParamValue(paramId, envId, body.get("paramValue"));
        return Result.ok("保存成功");
    }

    /** 删除某参数在某环境下的值 */
    @Audit(module = "全局参数", action = "DELETE", description = "删除环境参数值")
    @DeleteMapping("/values/{paramId}/{envId}")
    public Result<String> deleteValue(
            @PathVariable Long paramId,
            @PathVariable Long envId) {
        service.deleteParamValue(paramId, envId);
        return Result.ok("删除成功");
    }

    /** 新增参数定义 */
    @Audit(module = "全局参数", action = "CREATE")
    @PostMapping
    public Result<GlobalParamVO> create(@Valid @RequestBody GlobalParamDTO dto) {
        return Result.ok("添加成功", service.create(dto));
    }

    /** 修改参数定义 */
    @Audit(module = "全局参数", action = "UPDATE")
    @PutMapping("/{id}")
    public Result<GlobalParamVO> update(@PathVariable Long id, @Valid @RequestBody GlobalParamDTO dto) {
        return Result.ok("修改成功", service.update(id, dto));
    }

    /** 删除参数定义（含所有环境值） */
    @Audit(module = "全局参数", action = "DELETE")
    @DeleteMapping("/{id}")
    public Result<String> delete(@PathVariable Long id) {
        service.delete(id);
        return Result.ok("删除成功");
    }
}
