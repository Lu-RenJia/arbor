package com.arbor.mapper;

import com.arbor.entity.FieldMapping;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 字段映射 Mapper
 *
 * @author gwl
 */
@Mapper
public interface FieldMappingMapper {

    List<FieldMapping> selectByFlowId(Long flowId);

    FieldMapping selectById(Long id);

    int insert(FieldMapping entity);

    int update(FieldMapping entity);

    int deleteById(Long id);
}
