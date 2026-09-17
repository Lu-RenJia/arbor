package com.arbor.util;

import com.arbor.config.FileStorageProperties;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.file.*;
import java.util.UUID;

/**
 * 文件物理存储服务 — 统一管理文件的磁盘读写
 *
 * @author gwl
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FileStorageService {

    private final FileStorageProperties properties;

    private Path basePath;
    private Path outputBasePath;

    @PostConstruct
    void init() {
        this.basePath = resolvePath(properties.getStoragePath());
        this.outputBasePath = resolvePath(properties.getOutputPath());
        try {
            Files.createDirectories(basePath);
            Files.createDirectories(outputBasePath);
            log.info("文件存储目录已就绪: base={}, output={}", basePath, outputBasePath);
        } catch (IOException e) {
            throw new RuntimeException("无法创建文件存储目录", e);
        }
    }

    /** 解析配置路径为绝对路径 */
    private static Path resolvePath(String raw) {
        Path p = Paths.get(raw);
        if (!p.isAbsolute()) {
            p = Paths.get(System.getProperty("user.dir")).resolve(p);
        }
        return p.toAbsolutePath().normalize();
    }

    // ===================== 持久文件（示例文件等） =====================

    /**
     * 存储文件
     *
     * @param in           输入流
     * @param flowId       流程ID（用于目录隔离）
     * @param originalName 原始文件名（用于保留扩展名）
     * @return 相对存储路径（可用于后续加载/删除）
     */
    public String store(InputStream in, String flowId, String originalName) throws IOException {
        return storeTo(in, flowId, originalName, basePath);
    }

    /**
     * 加载文件
     *
     * @param relativePath 相对路径（store() 的返回值）
     * @return 文件输入流（调用方负责关闭）
     */
    public InputStream load(String relativePath) throws IOException {
        return loadFrom(relativePath, basePath);
    }

    // ===================== 输出临时文件（执行结果下载缓存） =====================

    /**
     * 存储输出文件（临时缓存，可定期清理）
     *
     * @param in           输入流
     * @param flowId       流程ID（用于目录隔离）
     * @param originalName 原始文件名（用于保留扩展名）
     * @return 相对存储路径
     */
    public String storeOutput(InputStream in, String flowId, String originalName) throws IOException {
        return storeTo(in, flowId, originalName, outputBasePath);
    }

    /**
     * 加载输出文件
     *
     * @param relativePath 相对路径（storeOutput() 的返回值）
     * @return 文件输入流
     */
    public InputStream loadOutput(String relativePath) throws IOException {
        return loadFrom(relativePath, outputBasePath);
    }

    // ===================== 内部实现 =====================

    private String storeTo(InputStream in, String flowId, String originalName, Path base) throws IOException {
        String ext = "";
        int dot = originalName.lastIndexOf('.');
        if (dot >= 0) ext = originalName.substring(dot);

        String fileName = UUID.randomUUID().toString().replace("-", "") + ext;
        Path flowDir = base.resolve(flowId);
        Files.createDirectories(flowDir);
        Path target = flowDir.resolve(fileName);

        try (in) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }
        String relativePath = flowId + "/" + fileName;
        log.info("文件已存储: {} -> {} ({} bytes)", originalName, relativePath, Files.size(target));
        return relativePath;
    }

    private InputStream loadFrom(String relativePath, Path base) throws IOException {
        Path target = base.resolve(relativePath).normalize();
        if (!target.startsWith(base)) {
            throw new SecurityException("非法文件路径: " + relativePath);
        }
        return Files.newInputStream(target);
    }

    /**
     * 获取文件大小
     */
    public long size(String relativePath) throws IOException {
        Path target = basePath.resolve(relativePath).normalize();
        if (!target.startsWith(basePath)) throw new SecurityException("非法文件路径: " + relativePath);
        return Files.size(target);
    }

    /**
     * 删除持久文件
     */
    public void delete(String relativePath) throws IOException {
        if (relativePath == null) return;
        deleteFrom(relativePath, basePath);
    }

    /**
     * 删除输出文件
     */
    public void deleteOutput(String relativePath) throws IOException {
        if (relativePath == null) return;
        deleteFrom(relativePath, outputBasePath);
    }

    private void deleteFrom(String relativePath, Path base) throws IOException {
        Path target = base.resolve(relativePath).normalize();
        if (!target.startsWith(base)) throw new SecurityException("非法文件路径: " + relativePath);
        Files.deleteIfExists(target);
        log.info("文件已删除: {}", relativePath);
    }

    @PreDestroy
    void destroy() {
        log.info("文件存储服务已关闭: base={}, output={}", basePath, outputBasePath);
    }
}
