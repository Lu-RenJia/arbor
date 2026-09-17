package com.arbor.controller;

import com.arbor.common.Audit;
import com.arbor.common.PageResult;
import com.arbor.common.Result;
import com.arbor.dto.DatasourceDTO;
import com.arbor.entity.DatasourceEnvConfig;
import com.arbor.service.DatasourceService;
import com.arbor.vo.DatasourceVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 数据源管理控制器
 *
 * @author gwl
 */
@Slf4j
@RestController
@RequestMapping("/api/datasource")
@RequiredArgsConstructor
public class DatasourceController {

    private final DatasourceService service;

    /** 分页查询 */
    @GetMapping("/page")
    public Result<PageResult<DatasourceVO>> page(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String sortField,
            @RequestParam(required = false) String sortOrder) {
        return Result.ok(service.page(pageNum, pageSize, keyword, sortField, sortOrder));
    }

    /** 新增 */
    @Audit(module = "数据源", action = "CREATE")
    @PostMapping
    public Result<DatasourceVO> create(@Valid @RequestBody DatasourceDTO dto) {
        return Result.ok("添加成功", service.create(dto));
    }

    /** 修改 */
    @Audit(module = "数据源", action = "UPDATE")
    @PutMapping("/{id}")
    public Result<DatasourceVO> update(@PathVariable Long id, @Valid @RequestBody DatasourceDTO dto) {
        return Result.ok("修改成功", service.update(id, dto));
    }

    /** 删除 */
    @Audit(module = "数据源", action = "DELETE")
    @DeleteMapping("/{id}")
    public Result<String> delete(@PathVariable Long id) {
        service.delete(id);
        return Result.ok("删除成功");
    }

    /** 测试连接（已保存的数据源） */
    @PostMapping("/test/{id}")
    public Result<String> testConnection(@PathVariable Long id) {
        boolean ok = service.testConnection(id);
        return ok ? Result.ok("连接成功") : Result.fail("连接失败");
    }

    /** 测试连接（未保存的配置，直接传入参数） */
    @PostMapping("/test")
    public Result<String> testConnectionRaw(@Valid @RequestBody DatasourceDTO dto) {
        boolean ok = service.testConnectionRaw(dto);
        return ok ? Result.ok("连接成功") : Result.fail("连接失败");
    }

    /** 查询数据源在各环境下的配置 */
    @GetMapping("/env-configs/{id}")
    public Result<List<DatasourceVO.DsEnvConfig>> listEnvConfigs(@PathVariable Long id) {
        return Result.ok(service.listEnvConfigs(id));
    }

    /** 保存数据源在指定环境下的配置 */
    @Audit(module = "数据源", action = "UPDATE", description = "保存环境配置")
    @PutMapping("/env-configs/{id}/{envId}")
    public Result<String> saveEnvConfig(@PathVariable Long id, @PathVariable Long envId, @RequestBody DatasourceEnvConfig config) {
        service.saveEnvConfig(id, envId, config);
        return Result.ok("保存成功");
    }

    /** 删除数据源在指定环境下的配置 */
    @Audit(module = "数据源", action = "DELETE", description = "删除环境配置")
    @DeleteMapping("/env-configs/{id}/{envId}")
    public Result<String> deleteEnvConfig(@PathVariable Long id, @PathVariable Long envId) {
        service.deleteEnvConfig(id, envId);
        return Result.ok("删除成功");
    }
}
