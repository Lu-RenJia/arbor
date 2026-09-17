package com.arbor.controller;

import com.arbor.common.PageResult;
import com.arbor.common.Result;
import com.arbor.common.TenantContext;
import com.arbor.entity.FileResource;
import com.arbor.service.FileResourceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;

/**
 * 文件资源管理接口
 *
 * @author gwl
 */
@Slf4j
@RestController
@RequestMapping("/api/file")
@RequiredArgsConstructor
public class FileResourceController {

    private final FileResourceService service;

    /** 上传流程示例文件 */
    @PostMapping("/upload/{flowId}")
    public Result<FileResource> upload(@PathVariable Long flowId, @RequestParam("file") MultipartFile file) {
        try {
            return Result.ok("上传成功", service.uploadExample(flowId, file));
        } catch (Exception e) {
            log.error("上传示例文件失败: flowId={}", flowId, e);
            return Result.fail("上传失败: " + e.getMessage());
        }
    }

    /** 下载文件（按 FileResource.id，跳过租户过滤以支持无认证下载） */
    @GetMapping("/download/{id}")
    public void download(@PathVariable Long id, jakarta.servlet.http.HttpServletResponse response) {
        try {
            TenantContext.skipTenant(true);
            FileResource fr = service.getById(id);
            if (fr == null) {
                response.setStatus(404);
                return;
            }
            InputStream in = service.download(fr.getId());
            response.setContentType(fr.getContentType() != null ? fr.getContentType() : "application/octet-stream");
            response.setHeader("Content-Disposition", "attachment; filename=\"" +
                    new String(fr.getFileName().getBytes(java.nio.charset.StandardCharsets.UTF_8), "ISO-8859-1") + "\"");
            response.setHeader("Content-Length", String.valueOf(fr.getFileSize()));
            response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
            response.setHeader("Pragma", "no-cache");
            try (in; java.io.OutputStream out = response.getOutputStream()) {
                in.transferTo(out);
            }
        } catch (Exception e) {
            log.error("下载文件失败: id={}", id, e);
            try { response.setStatus(500); } catch (Exception ignored) {}
        }
    }

    /** 删除文件 */
    @DeleteMapping("/{id}")
    public Result<String> delete(@PathVariable Long id) {
        try {
            service.delete(id);
            return Result.ok("删除成功");
        } catch (Exception e) {
            log.error("删除文件失败: id={}", id, e);
            return Result.fail("删除失败: " + e.getMessage());
        }
    }

    /** 文件管理分页列表 */
    @GetMapping("/page")
    public Result<PageResult<FileResource>> page(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String fileName,
            @RequestParam(required = false) Long flowId) {
        return Result.ok(service.pageFiles(pageNum, pageSize, fileName, flowId));
    }

    /** 获取流程的示例文件信息（不含文件内容） */
    @GetMapping("/flow/{flowId}")
    public Result<FileResource> getByFlowId(@PathVariable Long flowId) {
        FileResource fr = service.getByFlowId(flowId);
        return Result.ok(fr);
    }
}
