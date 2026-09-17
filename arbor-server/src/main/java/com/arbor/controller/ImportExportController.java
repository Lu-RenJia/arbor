package com.arbor.controller;

import com.arbor.common.Audit;
import com.arbor.common.Result;
import com.arbor.dto.ImportExportDTO;
import com.arbor.service.FlowExportService;
import com.arbor.service.FlowImportService;
import com.arbor.vo.ImportPreviewVO;
import com.arbor.vo.ImportResultVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 导入导出控制器
 *
 * @author gwl
 */
@Slf4j
@RestController
@RequestMapping("/api/import-export")
@RequiredArgsConstructor
public class ImportExportController {

    private final FlowExportService flowExportService;
    private final FlowImportService flowImportService;

    /** 导出流程（递归包含子流程与依赖资源），返回导入包 JSON */
    @Audit(module = "导入导出", action = "EXPORT", description = "导出流程")
    @PostMapping("/export-flows")
    public Result<String> exportFlows(@RequestBody ImportExportDTO dto) {
        return Result.ok(flowExportService.exportFlows(dto.getFlowIds()));
    }

    /** 导出应用（连带流程与全部依赖），返回导入包 JSON */
    @Audit(module = "导入导出", action = "EXPORT", description = "导出应用")
    @PostMapping("/export-apps")
    public Result<String> exportApps(@RequestBody ImportExportDTO dto) {
        return Result.ok(flowExportService.exportApps(dto.getAppIds()));
    }

    /** 导入预览：uid 比对生成冲突清单与环境缺失项 */
    @PostMapping("/preview")
    public Result<ImportPreviewVO> preview(@RequestBody ImportExportDTO dto) {
        return Result.ok(flowImportService.preview(dto.getPackageJson()));
    }

    /** 导入执行：按预览确认的策略 upsert + id 重映射 + 缓存失效 */
    @Audit(module = "导入导出", action = "IMPORT", description = "导入执行")
    @PostMapping("/execute")
    public Result<ImportResultVO> execute(@RequestBody ImportExportDTO dto) {
        return Result.ok("导入完成", flowImportService.execute(
                dto.getPackageJson(), dto.getDefaultConflictAction(), dto.getOverrides()));
    }
}
