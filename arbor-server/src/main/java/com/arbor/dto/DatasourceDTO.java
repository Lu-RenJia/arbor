package com.arbor.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 数据源 DTO
 *
 * @author gwl
 */
@Data
public class DatasourceDTO {

    /** 数据源名称 */
    @NotBlank(message = "数据源名称不能为空")
    private String name;

    /** 数据库类型（MYSQL/POSTGRESQL等） */
    @NotBlank(message = "数据源类型不能为空")
    private String dbType;

    /** 主机地址 */
    @NotBlank(message = "地址不能为空")
    private String host;

    /** 端口 */
    @NotNull(message = "端口不能为空")
    private Integer port;

    /** 数据库名 */
    @NotBlank(message = "数据库名不能为空")
    private String databaseName;

    /** 用户名 */
    @NotBlank(message = "用户名不能为空")
    private String username;

    /** 密码（新增时必填，修改时由 modifyPassword 控制） */
    private String password;

    /** 是否修改密码（仅修改时使用，默认 false） */
    private Boolean modifyPassword;

    /** 备注 */
    private String remark;

    // ========== 连接池配置（仅管理员可修改，NULL=系统默认值）==========

    /** 最小空闲连接 */
    private Integer poolMinIdle;
    /** 最大连接数 */
    private Integer poolMaxSize;
    /** 空闲超时（秒） */
    private Integer poolIdleTimeoutSec;
    /** 最大存活时间（秒） */
    private Integer poolMaxLifetimeSec;
    /** 获取连接超时（秒） */
    private Integer poolConnTimeoutSec;
}
