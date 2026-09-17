package com.arbor.vo;

import com.arbor.entity.ApiConfig;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 接口配置 VO
 *
 * @author gwl
 */
@Data
@Builder
public class ApiConfigVO {

    private Long id;
    /** API标识 */
    private String apiId;
    /** 接口名称 */
    private String name;
    /** 请求方式（GET/POST/PUT/DELETE） */
    private String method;
    /** 接口地址 */
    private String url;
    /** 超时时间（秒） */
    private Integer timeout;
    /** 请求头JSON */
    private String headers;
    /** 请求参数JSON */
    private String params;
    /** Body类型 */
    private String bodyType;
    /** Body内容 */
    private String bodyContent;
    /** 响应示例 */
    private String responseExample;
    /** 备注 */
    private String remark;
    /** 创建时间 */
    private LocalDateTime createTime;
    /** 更新时间 */
    private LocalDateTime updateTime;

    public static ApiConfigVO fromEntity(ApiConfig entity) {
        return ApiConfigVO.builder()
                .id(entity.getId())
                .apiId(entity.getApiId())
                .name(entity.getName())
                .method(entity.getMethod())
                .url(entity.getUrl())
                .timeout(entity.getTimeout())
                .headers(entity.getHeaders())
                .params(entity.getParams())
                .bodyType(entity.getBodyType())
                .bodyContent(entity.getBodyContent())
                .responseExample(entity.getResponseExample())
                .remark(entity.getRemark())
                .createTime(entity.getCreateTime())
                .updateTime(entity.getUpdateTime())
                .build();
    }
}
