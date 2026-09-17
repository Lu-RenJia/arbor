package com.arbor.service;

import com.arbor.common.AssertUtils;
import com.arbor.common.ErrorCode;
import com.arbor.common.TenantContext;
import com.arbor.dto.FieldMappingDTO;
import com.arbor.entity.FieldMapping;
import com.arbor.mapper.FieldMappingMapper;
import com.arbor.util.RedisCacheUtil;
import com.arbor.vo.FieldMappingVO;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 字段映射服务 — CRUD + Redis 缓存 + 结果数据映射转换
 *
 * @author gwl
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FieldMappingService {

    private final FieldMappingMapper mapper;
    private final RedisCacheUtil redisCacheUtil;
    private final ObjectMapper objectMapper;

    private static final String CACHE_PREFIX = "field_mapping:user";

    private String cacheKey(Long flowId) {
        Long userId = TenantContext.getUserId();
        return RedisCacheUtil.buildKey(CACHE_PREFIX, (userId != null ? userId : 0), "flow", flowId);
    }

    private void evictCache(Long flowId) {
        redisCacheUtil.evict(cacheKey(flowId));
    }

    /** 按流程ID查询映射列表（缓存） */
    public List<FieldMappingVO> listByFlowId(Long flowId) {
        String key = cacheKey(flowId);
        TypeReference<List<FieldMapping>> typeRef = new TypeReference<>() {};
        List<FieldMapping> list = redisCacheUtil.get(key, typeRef).orElseGet(() -> {
            List<FieldMapping> db = mapper.selectByFlowId(flowId);
            redisCacheUtil.set(key, db, RedisCacheUtil.randomizedTtl());
            return db;
        });
        return list.stream().map(FieldMappingVO::fromEntity).collect(Collectors.toList());
    }

    @Transactional
    public FieldMappingVO create(FieldMappingDTO dto) {
        FieldMapping entity = new FieldMapping();
        entity.setFlowId(dto.getFlowId());
        entity.setFieldName(dto.getFieldName());
        entity.setFieldLabel(dto.getFieldLabel());
        entity.setMappingJson(dto.getMappingJson());
        entity.setSortOrder(dto.getSortOrder() != null ? dto.getSortOrder() : 0);
        entity.setIsEnabled(dto.getIsEnabled() != null ? dto.getIsEnabled() : 1);
        entity.setRemark(dto.getRemark());
        mapper.insert(entity);
        evictCache(dto.getFlowId());
        log.info("新增字段映射: flowId={}, fieldName={}", dto.getFlowId(), dto.getFieldName());
        return FieldMappingVO.fromEntity(entity);
    }

    @Transactional
    public FieldMappingVO update(Long id, FieldMappingDTO dto) {
        FieldMapping entity = mapper.selectById(id);
        AssertUtils.notNull(entity, ErrorCode.FIELD_MAPPING_NOT_FOUND);
        entity.setFieldName(dto.getFieldName());
        entity.setFieldLabel(dto.getFieldLabel());
        entity.setMappingJson(dto.getMappingJson());
        entity.setSortOrder(dto.getSortOrder() != null ? dto.getSortOrder() : 0);
        entity.setIsEnabled(dto.getIsEnabled() != null ? dto.getIsEnabled() : 1);
        entity.setRemark(dto.getRemark());
        mapper.update(entity);
        evictCache(entity.getFlowId());
        log.info("修改字段映射: id={}, fieldName={}", id, dto.getFieldName());
        return FieldMappingVO.fromEntity(entity);
    }

    /** 批量保存：已存在的按 fieldName 更新，不存在的插入。返回最终列表并清缓存。 */
    @Transactional
    public List<FieldMappingVO> batchSave(Long flowId, List<FieldMappingDTO> list) {
        List<FieldMapping> existing = mapper.selectByFlowId(flowId);
        Map<String, FieldMapping> existingMap = new java.util.LinkedHashMap<>();
        for (FieldMapping fm : existing) {
            existingMap.put(fm.getFieldName(), fm);
        }
        for (FieldMappingDTO dto : list) {
            dto.setFlowId(flowId);
            FieldMapping old = existingMap.get(dto.getFieldName());
            if (old != null) {
                old.setFieldLabel(dto.getFieldLabel());
                old.setMappingJson(dto.getMappingJson());
                old.setSortOrder(dto.getSortOrder() != null ? dto.getSortOrder() : 0);
                old.setIsEnabled(dto.getIsEnabled() != null ? dto.getIsEnabled() : 1);
                old.setRemark(dto.getRemark());
                mapper.update(old);
            } else {
                FieldMapping entity = new FieldMapping();
                entity.setFlowId(flowId);
                entity.setFieldName(dto.getFieldName());
                entity.setFieldLabel(dto.getFieldLabel());
                entity.setMappingJson(dto.getMappingJson());
                entity.setSortOrder(dto.getSortOrder() != null ? dto.getSortOrder() : 0);
                entity.setIsEnabled(dto.getIsEnabled() != null ? dto.getIsEnabled() : 1);
                entity.setRemark(dto.getRemark());
                mapper.insert(entity);
            }
        }
        evictCache(flowId);
        log.info("批量保存字段映射: flowId={}, count={}", flowId, list.size());
        return listByFlowId(flowId);
    }

    @Transactional
    public void delete(Long id) {
        FieldMapping entity = mapper.selectById(id);
        AssertUtils.notNull(entity, ErrorCode.FIELD_MAPPING_NOT_FOUND);
        mapper.deleteById(id);
        evictCache(entity.getFlowId());
        log.info("删除字段映射: id={}, fieldName={}", id, entity.getFieldName());
    }

    /**
     * 对结果数据应用字段映射，返回适合前端渲染的结构
     * @param data  原始结果（Map 或 List&lt;Map&gt;）
     * @param flowId 流程ID
     * @return { columns: [{fieldName, fieldLabel, mappingJson}], rows: [Map] }
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> applyMapping(Object data, Long flowId) {
        List<FieldMappingVO> mappings = listByFlowId(flowId)
                .stream().filter(m -> m.getIsEnabled() == 1).collect(Collectors.toList());
        Map<String, FieldMappingVO> mappingMap = new LinkedHashMap<>();
        for (FieldMappingVO m : mappings) {
            mappingMap.put(m.getFieldName(), m);
        }

        boolean isArray = data instanceof List;
        List<Map<String, Object>> rows;
        if (isArray) {
            rows = (List<Map<String, Object>>) data;
        } else if (data instanceof Map) {
            rows = List.of((Map<String, Object>) data);
        } else {
            rows = List.of();
        }

        // 收集所有字段名（按 JSON 原始顺序）
        Map<String, Boolean> fieldOrder = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            for (String key : row.keySet()) {
                fieldOrder.putIfAbsent(key, true);
            }
        }

        // 按映射配置的排序号升序排列字段：已配置的在前（sortOrder 小的在前），未配置的保持 JSON 原始顺序跟在后面
        List<String> sortedFields = fieldOrder.keySet().stream()
                .sorted(Comparator
                        .comparing((String f) -> {
                            FieldMappingVO fm = mappingMap.get(f);
                            return fm != null && fm.getSortOrder() != null ? fm.getSortOrder() : Integer.MAX_VALUE;
                        })
                        .thenComparing(f -> !mappingMap.containsKey(f)))
                .collect(Collectors.toList());

        // 构建列定义
        List<Map<String, Object>> columns = sortedFields.stream().map(field -> {
            Map<String, Object> col = new LinkedHashMap<>();
            col.put("fieldName", field);
            FieldMappingVO fm = mappingMap.get(field);
            col.put("fieldLabel", fm != null ? fm.getFieldLabel() : field);
            col.put("mappingJson", fm != null ? fm.getMappingJson() : null);
            return col;
        }).collect(Collectors.toList());

        // 字典值替换（行内字段顺序与列定义保持一致）
        List<Map<String, Object>> mappedRows = rows.stream().map(row -> {
            Map<String, Object> mapped = new LinkedHashMap<>();
            for (String key : sortedFields) {
                Object val = row.get(key);
                FieldMappingVO fm = mappingMap.get(key);
                if (fm != null && fm.getMappingJson() != null && val != null) {
                    try {
                        Map<String, String> dict = objectMapper.readValue(
                                fm.getMappingJson(), new TypeReference<Map<String, String>>() {});
                        String strVal = val.toString();
                        val = dict.getOrDefault(strVal, strVal);
                    } catch (Exception ignored) {
                        // 映射 JSON 解析失败，保留原值
                    }
                }
                mapped.put(key, val);
            }
            return mapped;
        }).collect(Collectors.toList());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("columns", columns);
        result.put("rows", mappedRows);
        result.put("isArray", isArray);
        return result;
    }
}
