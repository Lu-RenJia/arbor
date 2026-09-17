package com.arbor.mapper;

import com.arbor.entity.FileResource;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 文件资源 Mapper
 *
 * @author gwl
 */
@Mapper
public interface FileResourceMapper {

    FileResource selectById(@Param("id") Long id);

    FileResource selectByFlowId(@Param("flowId") Long flowId);

    List<FileResource> selectPage(@Param("fileName") String fileName,
                                  @Param("flowId") Long flowId,
                                  @Param("userId") Long userId,
                                  @Param("offset") int offset,
                                  @Param("limit") int limit);

    long countPage(@Param("fileName") String fileName,
                   @Param("flowId") Long flowId,
                   @Param("userId") Long userId);

    int insert(FileResource file);

    int updateById(FileResource file);

    int deleteById(@Param("id") Long id);
}
