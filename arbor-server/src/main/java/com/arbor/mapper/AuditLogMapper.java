package com.arbor.mapper;

import com.arbor.entity.AuditLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 操作审计日志数据访问层
 *
 * @author gwl
 */
@Mapper
public interface AuditLogMapper {

    int insert(AuditLog auditLog);

    /** 分页查询（模块/用户名/关键字均可选） */
    List<AuditLog> selectPage(@Param("offset") long offset,
                              @Param("pageSize") int pageSize,
                              @Param("moduleName") String moduleName,
                              @Param("username") String username,
                              @Param("keyword") String keyword);

    long countPage(@Param("moduleName") String moduleName,
                   @Param("username") String username,
                   @Param("keyword") String keyword);
}
