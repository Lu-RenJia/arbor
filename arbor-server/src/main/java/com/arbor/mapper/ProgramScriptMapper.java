package com.arbor.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.arbor.entity.ProgramScript;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 程序脚本数据访问层（MyBatis XML）
 *
 * @author gwl
 */
@Mapper
public interface ProgramScriptMapper {

    /** 分页查询 */
    IPage<ProgramScript> selectPage(IPage<ProgramScript> page, @Param("keyword") String keyword,
                                    @Param("sortField") String sortField, @Param("sortOrder") String sortOrder);

    /** 根据ID查询 */
    ProgramScript selectById(Long id);

    /** 根据scriptId查询 */
    ProgramScript selectByScriptId(String scriptId);

    /** 插入（自动回填主键） */
    int insert(ProgramScript entity);

    /** 更新 */
    int update(ProgramScript entity);

    /** 删除 */
    int deleteById(Long id);

    // ====== 工具类相关 ======

    /** 查询所有工具类（is_tool=1） */
    List<ProgramScript> selectToolList();

    /** 根据工具类类名查询（用于唯一性校验） */
    ProgramScript selectByToolClassName(@Param("className") String className);

    /** 统计工具类总数 */
    int countTools();

    /** 插入工具类 */
    int insertTool(ProgramScript entity);

    /** 更新工具类 */
    int updateTool(ProgramScript entity);

    /** 引用追踪：查询全部程序脚本（用于扫描全局参数引用） */
    List<ProgramScript> selectAll();

    /** 内联资源：按流程ID查询 */
    List<ProgramScript> selectByFlowId(@Param("flowId") Long flowId);

    /** 内联资源：流程删除时级联清理 */
    int deleteByFlowId(@Param("flowId") Long flowId);
}
