package com.arbor.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 接口配置 DTO
 *
 * @author gwl
 */
@Data
public class ApiConfigDTO {

    /** 接口名称 */
    @NotBlank(message = "接口名称不能为空")
    private String name;

    /** 请求方式（GET/POST/PUT/DELETE） */
    @NotBlank(message = "请求方式不能为空")
    private String method;

    /** 接口地址 */
    @NotBlank(message = "接口地址不能为空")
    private String url;

    /** 超时时间（秒），默认30 */
    private Integer timeout;

    /** 请求头JSON */
    private String headers;

    /** 请求参数JSON */
    private String params;

    /** Body类型 */
    private String bodyType;

    /** Body内容 */
    private String bodyContent;

    /** 备注 */
    private String remark;

    /** 所属流程ID（内联模式时传入，全局资源不传） */
    private Long flowId;
}
