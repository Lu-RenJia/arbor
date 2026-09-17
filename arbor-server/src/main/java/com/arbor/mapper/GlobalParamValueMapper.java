package com.arbor.mapper;

import com.arbor.entity.GlobalParamValue;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 全局参数-环境值数据访问层（MyBatis XML）
 *
 * @author gwl
 */
@Mapper
public interface GlobalParamValueMapper {

    /** 按参数ID查询所有环境值 */
    List<GlobalParamValue> selectByParamId(Long paramId);

    /** 按环境ID查询所有参数值 */
    List<GlobalParamValue> selectByEnvId(Long envId);

    /** 批量按参数ID查询 */
    List<GlobalParamValue> selectByParamIds(@Param("paramIds") Set<Long> paramIds);

    /** 按参数ID+环境ID查单条 */
    GlobalParamValue selectByParamAndEnv(@Param("paramId") Long paramId, @Param("envId") Long envId);

    /** 按环境ID查询参数值Map (param_id → param_value) */
    @org.apache.ibatis.annotations.MapKey("paramId")
    Map<Long, GlobalParamValue> selectValueMapByEnvId(@Param("envId") Long envId);

    /** 插入 */
    int insert(GlobalParamValue entity);

    /** 更新 */
    int update(GlobalParamValue entity);

    /** 按参数ID+环境ID删除 */
    int deleteByParamAndEnv(@Param("paramId") Long paramId, @Param("envId") Long envId);

    /** 按参数ID删除所有环境值 */
    int deleteByParamId(Long paramId);
}
