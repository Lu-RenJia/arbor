package com.arbor.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 文件存储配置
 *
 * @author gwl
 */
@Data
@Component
@ConfigurationProperties(prefix = "arbor.file")
public class FileStorageProperties {

    /** 文件存储根路径（示例文件等持久存储） */
    private String storagePath = "./data/files";

    /** 输出文件临时路径（执行结果下载缓存，可定期清理） */
    private String outputPath = "./data/output";

    /** 最大上传大小（支持 MB/KB 后缀） */
    private String maxUploadSize = "10MB";
}
