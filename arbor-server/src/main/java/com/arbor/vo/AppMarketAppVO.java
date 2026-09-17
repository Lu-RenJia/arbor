package com.arbor.vo;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class AppMarketAppVO {
    private Long id;
    /** 广场ID */
    private Long marketId;
    /** 应用ID */
    private Long appId;
    /** 应用名称（JOIN填充） */
    private String appName;
    /** 流程名称（JOIN填充） */
    private String flowName;
    /** 流程ID（JOIN填充） */
    private Long flowId;
    /** 环境ID（共享记录中的） */
    private Long envId;
    /** 环境名称（JOIN填充） */
    private String envName;
    /** 流程是否开启文件上传（从 START 节点配置读取） */
    private boolean fileUploadEnabled;
    /** 流程是否开启示例文件（从 START 节点配置读取） */
    private boolean exampleEnabled;
    /** 参数表单 schema（从 START 节点配置读取，JSON 对象；未配置时为 null）。
     *  注意：不能直接存 Jackson2 的 JsonNode——Spring Boot 4 HTTP 层用 Jackson3 序列化，
     *  不认识 Jackson2 JsonNode 会退化为 Bean 序列化（输出 isXxx 内部属性），须转成纯 Map/List */
    private Object paramSchema;
    /** 示例文件ID（流程开启示例文件且已上传时有值） */
    private Long exampleFileId;
    /** 示例文件名称 */
    private String exampleFileName;
    /** 默认入参（JOIN填充） */
    private String inputParams;
    /** 共享操作人ID */
    private Long sharedBy;
    /** 共享操作人姓名（JOIN填充） */
    private String sharedByName;
    /** 共享时间 */
    private LocalDateTime createTime;
}
