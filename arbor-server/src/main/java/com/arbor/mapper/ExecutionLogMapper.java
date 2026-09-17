package com.arbor.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.arbor.entity.ExecutionLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

/**
 * 流程执行日志数据访问层（MyBatis XML）
 *
 * @author gwl
 */
@Mapper
public interface ExecutionLogMapper {

    /** 绕过 MyBatis Plus 自动租户过滤（COUNT 查询不会被过滤，导致 total 不准），改为 XML 里手动控制 */
    @InterceptorIgnore(tenantLine = "true")
    IPage<ExecutionLog> selectPage(IPage<ExecutionLog> page,
                                   @Param("flowName") String flowName,
                                   @Param("executeType") String executeType,
                                   @Param("status") String status,
                                   @Param("source") String source,
                                   @Param("ownerId") Long ownerId,
                                   @Param("executorId") Long executorId,
                                   @Param("startTime") LocalDateTime startTime,
                                   @Param("endTime") LocalDateTime endTime,
                                   @Param("filterUserId") Long filterUserId);

    /**
     * 按 ID 查单条 —— 与 {@link #selectPage} 同样绕过租户插件，归属校验由
     * {@link com.arbor.service.ExecutionLogService} 显式完成。
     * <p>
     * 此前未标注该注解，插件会自动追加 {@code user_id = 当前用户}；而列表页对管理员是放行看全部的，
     * 于是管理员能在列表看到他人日志、点详情却报「执行日志不存在」。两条路径的可见性口径必须一致，
     * 统一收归 Service 判定，不再一半靠插件、一半靠手写 SQL。
     * </p>
     */
    @InterceptorIgnore(tenantLine = "true")
    ExecutionLog selectById(Long id);

    int insert(ExecutionLog entity);
}
