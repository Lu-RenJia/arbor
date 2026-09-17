package com.arbor.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.arbor.entity.RedisScript;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface RedisScriptMapper {
    IPage<RedisScript> selectPage(IPage<RedisScript> page, @Param("keyword") String keyword,
                                  @Param("sortField") String sortField, @Param("sortOrder") String sortOrder);
    RedisScript selectById(Long id);
    int insert(RedisScript entity);
    int update(RedisScript entity);
    int deleteById(Long id);

    /** 引用追踪：查询全部 Redis 脚本（用于扫描全局参数引用）—— 含内联资源，故意不加 flow_id 过滤 */
    List<RedisScript> selectAll();

    /** 引用追踪：查找使用指定 Redis 数据源的脚本 */
    List<RedisScript> selectByDatasourceId(@Param("datasourceId") Long datasourceId);

    /** 内联资源：按流程ID查询（对齐 ApiConfigMapper/DbScriptMapper 同名方法） */
    List<RedisScript> selectByFlowId(@Param("flowId") Long flowId);

    /** 内联资源：流程删除时级联清理 */
    int deleteByFlowId(@Param("flowId") Long flowId);

    /** 数据源删除时级联清理（含内联配置），避免留下无法执行的孤儿配置 */
    int deleteByDatasourceId(@Param("datasourceId") Long datasourceId);

    /** 按业务前缀反查配置 —— D11 使用统计与 D8 删除保护都靠它 */
    List<RedisScript> selectByPrefixId(@Param("prefixId") Long prefixId);
}
