package com.arbor.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 操作审计日志实体（audit_log 表）
 *
 * @author gwl
 */
@Data
public class AuditLog {

    private Long id;

    /** 操作人ID（登录场景可能为空） */
    private Long userId;

    /** 操作人用户名 */
    private String username;

    /** 业务模块 */
    private String moduleName;

    /** 操作类型：CREATE / UPDATE / DELETE / LOGIN / LOGIN_FAILED / LOGOUT / IMPORT / EXPORT */
    private String action;

    /** 操作描述 */
    private String description;

    /** 请求参数快照（敏感字段已脱敏） */
    private String detail;

    /** HTTP 方法 */
    private String method;

    /** 请求路径 */
    private String uri;

    /** 来源IP */
    private String ip;

    /** 是否成功：1 成功 0 失败 */
    private Integer success;

    /** 失败原因 */
    private String errorMsg;

    /** 操作时间 */
    private LocalDateTime createTime;
}
