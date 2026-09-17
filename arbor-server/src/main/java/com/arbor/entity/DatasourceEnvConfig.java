package com.arbor.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 数据源-环境配置实体（按环境覆写 host/port/database/username/password 及连接池参数）
 *
 * @author gwl
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class DatasourceEnvConfig extends BaseEntity {

    /** 数据源ID (datasource.id) */
    private Long datasourceId;

    /** 环境ID (environment.id) */
    private Long envId;

    /** 所属用户ID（隔离用，取自所属数据源的 user_id） */
    private Long userId;

    /** 主机地址（覆写基表） */
    private String host;

    /** 端口（覆写基表） */
    private Integer port;

    /** 数据库名（覆写基表） */
    private String databaseName;

    /** 用户名（覆写基表） */
    private String username;

    /** 密码 AES 密文（覆写基表） */
    private String password;

    /** 连接池最小空闲连接 */
    private Integer poolMinIdle;

    /** 连接池最大连接数 */
    private Integer poolMaxSize;

    /** 空闲超时(秒) */
    private Integer poolIdleTimeoutSec;

    /** 连接最大存活时间(秒) */
    private Integer poolMaxLifetimeSec;

    /** 获取连接超时(秒) */
    private Integer poolConnTimeoutSec;
}
