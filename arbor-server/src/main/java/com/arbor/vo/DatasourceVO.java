package com.arbor.vo;

import com.arbor.service.DatasourceService;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 数据源 VO
 *
 * @author gwl
 */
@Data
@Builder
public class DatasourceVO {

    private Long id;
    /** 数据源名称 */
    private String name;
    /** 数据库类型（MYSQL/POSTGRESQL等） */
    private String dbType;
    /** 主机地址 */
    private String host;
    /** 端口 */
    private Integer port;
    /** 数据库名 */
    private String databaseName;
    /** 用户名 */
    private String username;
    /** 密码不返回明文，只返回脱敏提示 */
    private String passwordHint;
    /** 备注 */
    private String remark;
    /** 创建时间 */
    private LocalDateTime createTime;
    /** 更新时间 */
    private LocalDateTime updateTime;

    // 连接池配置
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

    // ====== 内嵌类：数据源环境配置 ======

    @Data
    @Builder
    public static class DsEnvConfig {
        /** 环境ID */
        private Long envId;
        /** 环境名称 */
        private String envName;
        /** 环境代码 */
        private String envCode;
        /** 状态："正常" 或 "已停用" */
        private String status;
        /** 主机地址 */
        private String host;
        /** 端口 */
        private Integer port;
        /** 数据库名 */
        private String databaseName;
        /** 用户名 */
        private String username;
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

    public static DatasourceVO fromEntity(com.arbor.entity.Datasource entity) {
        return DatasourceVO.builder()
                .id(entity.getId())
                .name(entity.getName())
                .dbType(entity.getDbType())
                .host(entity.getHost())
                .port(entity.getPort())
                .databaseName(entity.getDatabaseName())
                .username(entity.getUsername())
                .passwordHint(entity.getPassword() != null && !entity.getPassword().isEmpty() ? DatasourceService.PASSWORD_MASK : "")
                .remark(entity.getRemark())
                .createTime(entity.getCreateTime())
                .updateTime(entity.getUpdateTime())
                .poolMinIdle(entity.getPoolMinIdle())
                .poolMaxSize(entity.getPoolMaxSize())
                .poolIdleTimeoutSec(entity.getPoolIdleTimeoutSec())
                .poolMaxLifetimeSec(entity.getPoolMaxLifetimeSec())
                .poolConnTimeoutSec(entity.getPoolConnTimeoutSec())
                .build();
    }
}
