package com.arbor.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.arbor.common.TenantContext;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Set;

/**
 * MyBatis Plus 配置 — 分页插件 + 多租户隔离插件
 *
 * @author gwl
 */
@Configuration
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        // 顺序不可颠倒：多租户必须在分页之前注册。
        // 分页插件在 willDoQuery 阶段执行自动 COUNT，若此时租户插件尚未给 SQL 追加 user_id 条件，
        // COUNT 会统计全表（跨用户）而 LIST 已带租户过滤，导致 total 虚高、末页出现空页。
        // 多租户插件
        interceptor.addInnerInterceptor(new TenantLineInnerInterceptor(new ArborTenantHandler()));
        // 分页插件（自动 count + LIMIT，SQL 仍写在 XML 中）
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }

    /**
     * 共享表白名单 — 不参与多租户过滤。
     * <p>
     * 两类表：① 本身无 user_id 列的全局/共享资源；
     * ② 执行日志的两张子表 —— 它们的可见性由已受租户过滤的 execution_log 决定，
     * 而应用执行链路全程 skipTenant(true)、定时执行无用户上下文，写入时 user_id 落 0，
     * 若参与过滤则查详情时（正常租户上下文）会被追加 user_id=当前用户 而全部读不出来。
     * 权限不受影响：execution_log 主表仍受过滤，打不开别人的执行详情就查不到子表数据。
     * </p>
     * <p>
     * ⚠️ 不要给这两张子表加「按 user_id 直查」的接口，那会变成越权读取。
     * </p>
     */
    private static final Set<String> SHARED_TABLES = Set.of(
        "user", "department", "role", "permission", "role_permission", "menu", "user_role",
        "field_mapping",
        "app_market", "app_market_user", "app_market_app",
        "flow_snapshot", "flow_node_snapshot", "flow_schedule",
        "audit_log",
        "execution_node_log", "execution_script_log"
    );

    static class ArborTenantHandler implements TenantLineHandler {

        @Override
        public Expression getTenantId() {
            Long userId = TenantContext.getUserId();
            return userId != null ? new LongValue(userId) : new LongValue(0);
        }

        @Override
        public String getTenantIdColumn() {
            return "user_id";
        }

        @Override
        public boolean ignoreTable(String tableName) {
            // 跳过租户过滤标记（用于执行广场共享应用）
            if (TenantContext.isSkipTenant()) return true;
            // 全员隔离：管理员也只看自己的数据（user_id=自己），仅共享白名单表例外
            return SHARED_TABLES.contains(tableName.toLowerCase());
        }
    }
}
