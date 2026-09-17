package com.arbor.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 接口配置实体
 *
 * @author gwl
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ApiConfig extends BaseEntity {

    /** 接口ID（系统自动生成） */
    private String apiId;

    /** 接口名称 */
    private String name;

    /** 请求方式：GET/POST/PUT/DELETE */
    private String method;

    /** 接口地址（支持${}全局参数） */
    private String url;

    /** 超时时间（秒） */
    private Integer timeout;

    /** 请求头JSON */
    private String headers;

    /** 请求参数JSON */
    private String params;

    /** Body类型：none/form-data/x-www-form-urlencoded/json/raw */
    private String bodyType;

    /** Body内容（支持${}全局参数） */
    private String bodyContent;

    /** 响应示例 */
    private String responseExample;

    /** 备注 */
    private String remark;

    /** 所属流程ID（NULL=全局资源，非空=内联资源） */
    private Long flowId;

    /** 全局唯一标识（导入导出匹配用，应用层创建时生成UUID） */
    private String uid;
}
