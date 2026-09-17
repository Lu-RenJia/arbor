package com.arbor.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.arbor.entity.Environment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 环境配置数据访问层（MyBatis XML）
 *
 * @author gwl
 */
@Mapper
public interface EnvironmentMapper {

    /** 分页查询（排除已删除） */
    IPage<Environment> selectPage(IPage<Environment> page, @Param("keyword") String keyword,
                                  @Param("sortField") String sortField, @Param("sortOrder") String sortOrder);

    /** 根据ID查询 */
    Environment selectById(Long id);

    /** 根据编码查询 */
    Environment selectByCode(String code);

    /** 查询默认环境 */
    Environment selectDefault();

    /** 查询当前用户的活跃环境（排除已删除，按排序） */
    List<Environment> selectActive(@Param("userId") Long userId);

    /** 插入（自动回填主键） */
    int insert(Environment entity);

    /** 更新 */
    int update(Environment entity);

    /** 软删除 */
    int softDelete(@Param("id") Long id, @Param("deletedTime") java.time.LocalDateTime deletedTime);

    /** 清空当前用户的其他默认环境（排除指定ID，租户插件自动加 user_id 过滤） */
    int clearDefaultExcept(@Param("excludeId") Long excludeId);
}
