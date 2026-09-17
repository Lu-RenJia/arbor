package com.arbor.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class RedisScriptDTO {
    /** 名称 */
    @NotBlank(message = "名称不能为空") private String name;
    /** 关联的 Redis 数据源ID */
    private Long datasourceId;
    /** 关联的业务前缀ID（必选，必须属于所选数据源） */
    @NotNull(message = "请选择业务前缀") private Long prefixId;
    /** 命令: GET/SET/DEL/EXPIRE/TTL/EXISTS/GET&SET */
    @NotBlank(message = "请选择命令") private String command;
    /** Redis Key（支持 ${} 表达式；不含任何系统前缀，最终 key = {业务前缀}:{此值}） */
    @NotBlank(message = "Redis Key 不能为空") private String redisKey;
    /** Redis Value（支持 ${} 表达式，GET/GET&SET 无需配置） */
    private String redisValue;
    /** 过期时间 */
    private Integer ttl;
    /** TTL单位: SECONDS/MINUTES/HOURS/DAYS，空默认 SECONDS */
    private String ttlUnit;
    /** 备注 */
    private String remark;
    /** 所属流程ID（内联模式时传入，全局资源不传；仅新建生效，修改不改归属） */
    private Long flowId;
}
