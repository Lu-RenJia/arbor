package com.arbor.service;

import com.arbor.common.PageResult;
import com.arbor.common.TenantContext;
import com.arbor.entity.FileResource;
import com.arbor.mapper.FileResourceMapper;
import com.arbor.util.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * 文件资源业务服务
 *
 * @author gwl
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileResourceService {

    private final FileResourceMapper fileResourceMapper;
    private final FileStorageService fileStorageService;

    /**
     * 上传流程示例文件（逻辑ID稳定不变）
     * - 已有记录：原地 UPDATE（ID不变，仅替换物理文件）
     * - 首次上传：INSERT 新记录，返回的 id 写入流程 config.exampleFileId 后永不变更
     */
    @Transactional
    public FileResource uploadExample(Long flowId, MultipartFile file) throws IOException {
        FileResource existing = fileResourceMapper.selectByFlowId(flowId);
        // 存储新物理文件
        String relativePath = fileStorageService.store(file.getInputStream(), String.valueOf(flowId), file.getOriginalFilename());

        if (existing != null) {
            // 原地更新：删除旧物理文件，更新记录（ID不变）
            try { fileStorageService.delete(existing.getFilePath()); } catch (Exception e) { log.warn("删除旧示例文件失败: {}", e.getMessage()); }
            existing.setFileName(file.getOriginalFilename());
            existing.setFilePath(relativePath);
            existing.setFileSize(file.getSize());
            existing.setContentType(file.getContentType());
            fileResourceMapper.updateById(existing);
            log.info("示例文件已更新(原地): flowId={}, fileName={}, id={}", flowId, file.getOriginalFilename(), existing.getId());
            return existing;
        }

        // 首次上传：INSERT
        FileResource fr = new FileResource();
        fr.setFlowId(flowId);
        fr.setFileName(file.getOriginalFilename());
        fr.setFilePath(relativePath);
        fr.setFileSize(file.getSize());
        fr.setContentType(file.getContentType());
        fr.setFileType("EXAMPLE");
        fr.setUserId(TenantContext.getUserId());
        fileResourceMapper.insert(fr);
        log.info("示例文件已创建: flowId={}, fileName={}, id={}", flowId, file.getOriginalFilename(), fr.getId());
        return fr;
    }

    /**
     * 获取流程的示例文件
     */
    public FileResource getByFlowId(Long flowId) {
        return fileResourceMapper.selectByFlowId(flowId);
    }

    /**
     * 下载文件（根据类型选择存储位置）
     */
    public InputStream download(Long id) throws IOException {
        FileResource fr = fileResourceMapper.selectById(id);
        if (fr == null) throw new IllegalArgumentException("文件不存在");
        if ("OUTPUT".equals(fr.getFileType())) {
            return fileStorageService.loadOutput(fr.getFilePath());
        }
        return fileStorageService.load(fr.getFilePath());
    }

    /**
     * 删除文件（根据类型选择存储位置）
     */
    @Transactional
    public void delete(Long id) throws IOException {
        FileResource fr = fileResourceMapper.selectById(id);
        if (fr == null) return;
        if ("OUTPUT".equals(fr.getFileType())) {
            fileStorageService.deleteOutput(fr.getFilePath());
        } else {
            fileStorageService.delete(fr.getFilePath());
        }
        fileResourceMapper.deleteById(id);
    }

    /**
     * 按 ID 查询文件（供下载/引用）
     */
    public FileResource getById(Long id) {
        return fileResourceMapper.selectById(id);
    }

    /**
     * 创建流程输出文件（每次执行创建一个新文件，不替换旧文件）
     */
    @Transactional
    public FileResource createOutput(Long flowId, String fileName, InputStream in,
                                      String contentType, long fileSize) throws IOException {
        String relativePath = fileStorageService.storeOutput(in, String.valueOf(flowId), fileName);
        FileResource fr = new FileResource();
        fr.setFlowId(flowId);
        fr.setFileName(fileName);
        fr.setFilePath(relativePath);
        fr.setFileSize(fileSize);
        fr.setContentType(contentType != null ? contentType : "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        fr.setFileType("OUTPUT");
        fr.setUserId(TenantContext.getUserId());
        fileResourceMapper.insert(fr);
        log.info("输出文件已创建: flowId={}, fileName={}, id={}", flowId, fileName, fr.getId());
        return fr;
    }

    /**
     * 文件管理分页
     */
    public PageResult<FileResource> pageFiles(int pageNum, int pageSize, String fileName, Long flowId) {
        int offset = (pageNum - 1) * pageSize;
        Long userId = TenantContext.getUserId();
        List<FileResource> list = fileResourceMapper.selectPage(fileName, flowId, userId, offset, pageSize);
        long total = fileResourceMapper.countPage(fileName, flowId, userId);
        return PageResult.of(pageNum, pageSize, total, list);
    }
}
