package com.arbor.datasource;

import com.arbor.entity.Datasource;
import com.arbor.util.AesUtil;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 动态数据源管理器
 * 管理用户配置数据源的 HikariCP 连接池，避免每次查询都新建物理连接
 *
 * @author gwl
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DynamicDataSourceManager {

    private final AesUtil aesUtil;

    /** 缓存已加载的驱动 */
    private static final Map<String, Boolean> DRIVER_LOADED = new ConcurrentHashMap<>();

    /** 动态数据源连接池缓存，key = datasourceId */
    private final Map<Long, HikariDataSource> poolCache = new ConcurrentHashMap<>();

    /** Oracle 数据库类型标识 */
    private static final String DB_TYPE_ORACLE = "ORACLE";
    /** Oracle JDBC 驱动类名 */
    private static final String ORACLE_DRIVER = "oracle.jdbc.OracleDriver";
    /** MySQL JDBC 驱动类名 */
    private static final String MYSQL_DRIVER = "com.mysql.cj.jdbc.Driver";
    /** 连接有效性检查超时（秒） */
    private static final int CONNECTION_VALID_TIMEOUT = 5;
    /** JDBC URL 模板 - Oracle */
    private static final String URL_ORACLE = "jdbc:oracle:thin:@%s:%d:%s";
    /** JDBC URL 模板 - MySQL */
    private static final String URL_MYSQL = "jdbc:mysql://%s:%d/%s?useUnicode=true&characterEncoding=UTF-8&useSSL=false&serverTimezone=Asia/Shanghai";

    /** 系统默认：最小空闲连接 */
    private static final int DEFAULT_POOL_MIN_IDLE = 1;
    /** 系统默认：最大连接数 */
    private static final int DEFAULT_POOL_MAX_SIZE = 5;
    /** 系统默认：空闲超时（秒） */
    private static final int DEFAULT_POOL_IDLE_TIMEOUT_SEC = 180;
    /** 系统默认：连接最大生存时间（秒） */
    private static final int DEFAULT_POOL_MAX_LIFETIME_SEC = 300;
    /** 系统默认：获取连接超时（秒） */
    private static final int DEFAULT_POOL_CONN_TIMEOUT_SEC = 30;

    /**
     * 根据数据源配置获取 JDBC 连接（从 HikariCP 连接池获取，首次调用自动建池）
     *
     * @param ds 数据源实体（已解析全局参数）
     * @return JDBC Connection
     */
    public Connection getConnection(Datasource ds) throws SQLException {
        HikariDataSource pool = poolCache.computeIfAbsent(ds.getId(), id -> createPool(ds));
        if (pool.isClosed()) {
            // 池被外部关闭（配置变更驱逐后），重建
            pool = createPool(ds);
            poolCache.put(ds.getId(), pool);
        }
        String dbType = ds.getDbType().toUpperCase();
        ensureDriverLoaded(dbType);
        return pool.getConnection();
    }

    /**
     * 测试数据源连接是否可用
     */
    public boolean testConnection(Datasource ds) {
        try {
            if (ds.getId() == null) {
                // 未保存的配置（新增/修改密码后测试）：一次性建池验证后立即关闭，不进缓存（避免 null key）
                HikariDataSource pool = createPool(ds);
                try (Connection conn = pool.getConnection()) {
                    return conn.isValid(CONNECTION_VALID_TIMEOUT);
                } finally {
                    pool.close();
                }
            }
            try (Connection conn = getConnection(ds)) {
                return conn.isValid(CONNECTION_VALID_TIMEOUT);
            }
        } catch (Exception e) {
            log.warn("数据源连接测试失败: {} -> {}", ds.getName(), e.getMessage());
            return false;
        }
    }

    /**
     * 驱逐指定数据源的连接池（数据源配置变更或删除时调用）
     *
     * @param datasourceId 数据源ID
     */
    public void evictPool(Long datasourceId) {
        HikariDataSource removed = poolCache.remove(datasourceId);
        if (removed != null && !removed.isClosed()) {
            removed.close();
            log.info("已关闭动态数据源连接池: dsId={}", datasourceId);
        }
    }

    /**
     * 创建 HikariCP 连接池
     * 优先使用数据源上配置的池参数，NULL 则用系统默认值
     */
    private HikariDataSource createPool(Datasource ds) {
        String password = aesUtil.decrypt(ds.getPassword());
        String url = buildJdbcUrl(ds);
        String driverClass = DB_TYPE_ORACLE.equals(ds.getDbType().toUpperCase())
                ? ORACLE_DRIVER : MYSQL_DRIVER;

        int minIdle = ds.getPoolMinIdle() != null ? ds.getPoolMinIdle() : DEFAULT_POOL_MIN_IDLE;
        int maxSize = ds.getPoolMaxSize() != null ? ds.getPoolMaxSize() : DEFAULT_POOL_MAX_SIZE;
        long idleTimeoutMs = (ds.getPoolIdleTimeoutSec() != null ? ds.getPoolIdleTimeoutSec() : DEFAULT_POOL_IDLE_TIMEOUT_SEC) * 1000L;
        long maxLifetimeMs = (ds.getPoolMaxLifetimeSec() != null ? ds.getPoolMaxLifetimeSec() : DEFAULT_POOL_MAX_LIFETIME_SEC) * 1000L;
        long connTimeoutMs = (ds.getPoolConnTimeoutSec() != null ? ds.getPoolConnTimeoutSec() : DEFAULT_POOL_CONN_TIMEOUT_SEC) * 1000L;

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setUsername(ds.getUsername());
        config.setPassword(password);
        config.setDriverClassName(driverClass);
        config.setMinimumIdle(minIdle);
        config.setMaximumPoolSize(maxSize);
        config.setIdleTimeout(idleTimeoutMs);
        config.setMaxLifetime(maxLifetimeMs);
        config.setConnectionTimeout(connTimeoutMs);
        config.setPoolName("DynamicPool-" + ds.getId());

        log.info("创建动态数据源连接池: dsId={}, name={}, minIdle={}, maxSize={}, idleTimeout={}s, maxLifetime={}s",
                ds.getId(), ds.getName(), minIdle, maxSize,
                idleTimeoutMs / 1000, maxLifetimeMs / 1000);
        return new HikariDataSource(config);
    }

    /** 应用关闭时释放所有动态连接池 */
    @PreDestroy
    public void destroy() {
        poolCache.forEach((id, pool) -> {
            if (!pool.isClosed()) {
                pool.close();
                log.info("应用关闭，释放动态连接池: dsId={}", id);
            }
        });
        poolCache.clear();
    }

    /**
     * 确保数据库驱动已加载
     */
    private void ensureDriverLoaded(String dbType) {
        DRIVER_LOADED.computeIfAbsent(dbType, type -> {
            try {
                String driverClass = switch (type) {
                    case DB_TYPE_ORACLE -> ORACLE_DRIVER;
                    default -> MYSQL_DRIVER;
                };
                Class.forName(driverClass);
                log.info("数据库驱动加载成功: {} ({})", type, driverClass);
                return true;
            } catch (ClassNotFoundException e) {
                log.error("数据库驱动加载失败: {}", type, e);
                return false;
            }
        });
    }

    /**
     * 构建 JDBC 连接 URL
     */
    public static String buildJdbcUrl(Datasource ds) {
        String dbType = ds.getDbType().toUpperCase();
        String host = ds.getHost();
        int port = ds.getPort();
        String dbName = ds.getDatabaseName();

        if (DB_TYPE_ORACLE.equals(dbType)) {
            return String.format(URL_ORACLE, host, port, dbName);
        }
        // 默认 MySQL
        return String.format(URL_MYSQL, host, port, dbName);
    }
}
