package com.arbor.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 文件资源实体 — 存储流程示例文件
 *
 * @author gwl
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class FileResource extends BaseEntity {

    /** 所属流程ID */
    private Long flowId;
    /** 原始文件名 */
    private String fileName;
    /** 相对存储路径 */
    private String filePath;
    /** 文件字节数 */
    private Long fileSize;
    /** MIME类型 */
    private String contentType;
    /** 文件类型：EXAMPLE=示例文件 */
    private String fileType;
    /** 备注 */
    private String description;
    /** 租户ID */
    private Long userId;

    // ===== 查询时 JOIN 填充 =====
    /** 流程名称 */
    private transient String flowName;
    /** 应用名称（逗号分隔） */
    private transient String appNames;
}
