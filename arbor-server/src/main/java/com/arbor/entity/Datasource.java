package com.arbor.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 数据源配置实体
 *
 * @author gwl
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class Datasource extends BaseEntity {

    /** 所属用户ID（多租户隔离用） */
    private Long userId;

    /** 数据源名称 */
    private String name;

    /** 数据源类型：MYSQL / ORACLE */
    private String dbType;

    /** 地址 */
    private String host;

    /** 端口 */
    private Integer port;

    /** 数据库名 */
    private String databaseName;

    /** 用户名 */
    private String username;

    /** 密码（加密存储） */
    private String password;

    /** 备注 */
    private String remark;

    // ========== 连接池配置（NULL=系统默认值，仅管理员可修改）==========

    /** 最小空闲连接，默认1 */
    private Integer poolMinIdle;
    /** 最大连接数，默认5 */
    private Integer poolMaxSize;
    /** 空闲超时（秒），默认180 */
    private Integer poolIdleTimeoutSec;
    /** 最大存活时间（秒），默认300 */
    private Integer poolMaxLifetimeSec;
    /** 获取连接超时（秒），默认30 */
    private Integer poolConnTimeoutSec;

    /** 全局唯一标识（导入导出匹配用，应用层创建时生成UUID） */
    private String uid;
}
