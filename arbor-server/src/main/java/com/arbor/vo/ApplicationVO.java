package com.arbor.vo;

import com.arbor.entity.Application;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ApplicationVO {

    private Long id;
    /** 应用名称 */
    private String name;
    /** 关联的流程ID */
    private Long flowId;
    /** 流程名称（JOIN填充） */
    private String flowName;
    /** 执行环境ID */
    private Long envId;
    /** 环境名称（JOIN填充） */
    private String envName;
    /** 默认入参 */
    private String inputParams;
    /** 备注 */
    private String remark;
    /** 流程是否开启文件上传（START config 带出） */
    @Builder.Default
    private boolean fileUploadEnabled = false;
    /** 流程是否开启示例文件（START config 带出） */
    @Builder.Default
    private boolean exampleEnabled = false;
    /** 参数表单 schema（START config 带出，JSON 对象；未配置时为 null）。
     *  注意：不能直接存 Jackson2 的 JsonNode——Spring Boot 4 HTTP 层用 Jackson3 序列化，
     *  不认识 Jackson2 JsonNode 会退化为 Bean 序列化（输出 isXxx 内部属性），须转成纯 Map/List */
    private Object paramSchema;
    /** 示例文件ID（稳定逻辑ID） */
    private Long exampleFileId;
    /** 示例文件名 */
    private String exampleFileName;
    /** 创建时间 */
    private LocalDateTime createTime;
    /** 更新时间 */
    private LocalDateTime updateTime;

    public static ApplicationVO fromEntity(Application entity, String flowName, String envName) {
        return ApplicationVO.builder()
                .id(entity.getId())
                .name(entity.getName())
                .flowId(entity.getFlowId())
                .flowName(flowName)
                .envId(entity.getEnvId())
                .envName(envName)
                .inputParams(entity.getInputParams())
                .remark(entity.getRemark())
                .createTime(entity.getCreateTime())
                .updateTime(entity.getUpdateTime())
                .build();
    }

    public static ApplicationVO fromEntity(Application entity, String flowName) {
        return fromEntity(entity, flowName, null);
    }
}
