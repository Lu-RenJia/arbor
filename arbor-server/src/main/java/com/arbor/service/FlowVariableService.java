package com.arbor.service;

import com.arbor.common.AssertUtils;
import com.arbor.common.EnvironmentContext;
import com.arbor.common.ErrorCode;
import com.arbor.common.TenantContext;
import com.arbor.dto.FlowVariableDTO;
import com.arbor.engine.BuiltinVariable;
import com.arbor.engine.NodeResult;
import com.arbor.entity.FlowNode;
import com.arbor.entity.FlowVariable;
import com.arbor.entity.GlobalParam;
import com.arbor.mapper.FlowNodeMapper;
import com.arbor.mapper.FlowVariableMapper;
import com.arbor.service.EnvironmentService;
import com.arbor.util.RedisCacheUtil;
import com.arbor.vo.FlowVariableVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.arbor.entity.Datasource;
import com.arbor.groovy.tool.SqlTool;
import com.arbor.mapper.DatasourceMapper;
import com.arbor.datasource.DynamicDataSourceManager;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 流程变量池服务
 *
 * @author gwl
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlowVariableService {

    private final FlowVariableMapper mapper;
    private final GlobalParamService globalParamService;
    private final EnvironmentService environmentService;
    private final FlowNodeMapper flowNodeMapper;
    private final DatasourceMapper datasourceMapper;
    private final DynamicDataSourceManager dsManager;
    private final RedisCacheUtil redisCacheUtil;

    /** 查询最大返回行数 */
    @Value("${arbor.sql.max-rows:200}")
    private int sqlMaxRows;

    /** 流程变量列表缓存 key 前缀（按用户隔离） */
    static final String FV_CACHE_PREFIX = "flow_variables:user";

    private String fvCacheKey(Long flowId) {
        Long userId = TenantContext.getUserId();
        return RedisCacheUtil.buildKey(FV_CACHE_PREFIX, (userId != null ? userId : 0), "flowId", flowId);
    }

    /** 根据流程ID查询变量列表（含实时全局参数信息，环境感知） */
    public List<FlowVariableVO> listByFlowId(Long flowId) {
        List<FlowVariable> list = getVariablesByFlowIdCached(flowId);
        // 批量加载全局参数、节点和数据源
        Map<Long, GlobalParam> paramMap = batchLoadParams(list);
        Map<Long, FlowNode> nodeMap = batchLoadNodes(list);
        Map<Long, String> envValueMap = batchLoadEnvValues();
        Map<Long, Datasource> dsMap = batchLoadDatasources(list);

        return list.stream().map(v -> {
            FlowVariableVO vo = FlowVariableVO.fromEntity(v);
            if (v.getParamId() != null) {
                GlobalParam gp = paramMap.get(v.getParamId());
                if (gp != null) {
                    vo.setParamValue(envValueMap.getOrDefault(v.getParamId(), "(未配置)"));
                    vo.setParamName(gp.getParamName());
                    vo.setParamRemark(gp.getRemark());
                }
            }
            if (v.getNodeId() != null) {
                FlowNode fn = nodeMap.get(v.getNodeId());
                if (fn != null) {
                    vo.setNodeCode(fn.getNodeCode());
                    vo.setNodeName(fn.getNodeName());
                }
            }
            if (v.getDatasourceId() != null) {
                Datasource ds = dsMap.get(v.getDatasourceId());
                if (ds != null) {
                    vo.setDatasourceName(ds.getName());
                }
            }
            return vo;
        }).collect(Collectors.toList());
    }

    @Transactional
    public FlowVariableVO create(FlowVariableDTO dto) {
        AssertUtils.isFalse(dto.getParamId() == null && dto.getNodeCode() == null && dto.getNodeId() == null && dto.getDatasourceId() == null, ErrorCode.FLOW_VARIABLE_REQUIRED);
        FlowVariable entity = new FlowVariable();
        entity.setFlowId(dto.getFlowId());
        entity.setVarName(dto.getVarName());
        entity.setParamId(dto.getParamId());
        entity.setNodeId(dto.getNodeId());
        entity.setNodeCode(dto.getNodeCode());
        entity.setDatasourceId(dto.getDatasourceId());
        mapper.insert(entity);
        redisCacheUtil.evict(fvCacheKey(dto.getFlowId()));
        log.info("新增流程变量: flowId={}, varName={}, nodeCode={}", dto.getFlowId(), dto.getVarName(), dto.getNodeCode());
        return FlowVariableVO.fromEntity(entity);
    }

    @Transactional
    public FlowVariableVO update(Long id, FlowVariableDTO dto) {
        FlowVariable entity = mapper.selectById(id);
        AssertUtils.notNull(entity, ErrorCode.FLOW_VARIABLE_NOT_FOUND);
        AssertUtils.isFalse(dto.getParamId() == null && dto.getNodeCode() == null && dto.getNodeId() == null && dto.getDatasourceId() == null, ErrorCode.FLOW_VARIABLE_REQUIRED);
        entity.setVarName(dto.getVarName());
        entity.setParamId(dto.getParamId());
        entity.setNodeId(dto.getNodeId());
        entity.setNodeCode(dto.getNodeCode());
        entity.setDatasourceId(dto.getDatasourceId());
        mapper.update(entity);
        redisCacheUtil.evict(fvCacheKey(entity.getFlowId()));
        log.info("修改流程变量: id={}, varName={}, nodeCode={}", id, dto.getVarName(), dto.getNodeCode());
        return FlowVariableVO.fromEntity(entity);
    }

    @Transactional
    public void delete(Long id) {
        FlowVariable entity = mapper.selectById(id);
        AssertUtils.notNull(entity, ErrorCode.FLOW_VARIABLE_NOT_FOUND);
        mapper.deleteById(id);
        redisCacheUtil.evict(fvCacheKey(entity.getFlowId()));
    }

    @Transactional
    public void deleteBatch(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return;
        mapper.deleteByIds(ids);
        // 按模糊模式清除所有变量缓存（简单可靠）
        redisCacheUtil.evictByPattern(RedisCacheUtil.buildKey("flow_variables:user", "*"));
    }

    /** 带 Redis 缓存的按 flowId 查询变量列表 */
    private List<FlowVariable> getVariablesByFlowIdCached(Long flowId) {
        String key = fvCacheKey(flowId);
        com.fasterxml.jackson.core.type.TypeReference<List<FlowVariable>> typeRef =
                new com.fasterxml.jackson.core.type.TypeReference<>() {};
        return redisCacheUtil.get(key, typeRef).orElseGet(() -> {
            List<FlowVariable> list = mapper.selectByFlowId(flowId);
            redisCacheUtil.set(key, list, com.arbor.util.RedisCacheUtil.randomizedTtl());
            return list;
        });
    }

    /**
     * 解析变量池为 Groovy Binding 可用的 kv Map（环境感知，严格模式：全局参数缺失时抛异常）
     */
    public Map<String, Object> resolveVariables(Long flowId,
                                                  Map<String, NodeResult> previousResults) {
        return resolveVariables(flowId, previousResults, false);
    }

    /**
     * 解析变量池为 kv Map（环境感知）。
     *
     * @param lenient true=宽松模式（全局参数缺失时跳过该变量，不抛异常），适用于 API ${} 解析等场景；
     *                false=严格模式（缺失即报错），适用于 Groovy 脚本绑定。
     */
    public Map<String, Object> resolveVariables(Long flowId,
                                                  Map<String, NodeResult> previousResults,
                                                  boolean lenient) {
        List<FlowVariable> variables = getVariablesByFlowIdCached(flowId);
        if (variables.isEmpty()) {
            return autoInjectFlowParam(previousResults, new HashMap<>());
        }
        // 批量加载
        Map<Long, GlobalParam> paramMap = batchLoadParams(variables);
        Map<Long, String> envValueMap = batchLoadEnvValues();

        Map<String, Object> result = new HashMap<>();
        for (FlowVariable v : variables) {
            if (v.getParamId() != null) {
                GlobalParam gp = paramMap.get(v.getParamId());
                if (gp != null) {
                    String val = envValueMap.get(v.getParamId());
                    if (val == null && lenient) {
                        log.debug("宽松模式跳过无值全局参数变量: varName={}, paramName={}", v.getVarName(), gp.getParamName());
                        continue;
                    }
                    String envHint = "";
                    Long envId = EnvironmentContext.getEnvId();
                    if (envId != null) {
                        envHint = "（当前环境ID=" + envId + "，请先在全局参数管理中为该环境设置参数值）";
                    }
                    AssertUtils.notNull(val, ErrorCode.VAR_PARAM_MISSING_IN_ENV, gp.getParamName(), v.getVarName(), envHint);
                    result.put(v.getVarName(), val);
                }
            } else if (v.getNodeCode() != null) {
                // node_code 稳定标识，直接查 previousResults，不再依赖 nodeId
                if (previousResults != null) {
                    NodeResult nr = previousResults.get(v.getNodeCode());
                    if (nr != null && nr.isSuccess()) {
                        result.put(v.getVarName(), nr.getData());
                    } else {
                        result.put(v.getVarName(), null);
                    }
                } else {
                    result.put(v.getVarName(), null);
                }
            } else if (v.getNodeId() != null) {
                // 兼容旧数据：node_code 为空但 nodeId 不为空时走旧逻辑
                FlowNode fn = flowNodeMapper.selectById(v.getNodeId());
                if (fn != null && previousResults != null) {
                    NodeResult nr = previousResults.get(fn.getNodeCode());
                    if (nr != null && nr.isSuccess()) {
                        result.put(v.getVarName(), nr.getData());
                    } else {
                        result.put(v.getVarName(), null);
                    }
                } else {
                    result.put(v.getVarName(), null);
                }
            } else if (v.getDatasourceId() != null) {
                Datasource ds = datasourceMapper.selectById(v.getDatasourceId());
                AssertUtils.notNull(ds, com.arbor.common.ErrorCode.DATASOURCE_NOT_FOUND,
                        "datasourceId=" + v.getDatasourceId());
                result.put(v.getVarName(), new SqlTool(ds, dsManager, sqlMaxRows));
            }
        }
        return autoInjectFlowParam(previousResults, result);
    }

    /** 批量加载当前环境下的参数值（param_id → param_value） */
    private Map<Long, String> batchLoadEnvValues() {
        Long envId = EnvironmentContext.getEnvIdOrDefault();
        if (envId == null) {
            envId = environmentService.getDefaultEnvId();
        }
        return globalParamService.getEnvParamValueMap(envId);
    }

    /** 批量加载变量关联的全局参数（走 Redis 缓存） */
    private Map<Long, GlobalParam> batchLoadParams(List<FlowVariable> variables) {
        boolean hasParams = variables.stream().anyMatch(v -> v.getParamId() != null);
        if (!hasParams) return Map.of();
        // 一次性加载当前用户全量参数（由 GlobalParamService 缓存），再内存过滤
        return globalParamService.getAllParamMap();
    }

    /** 批量加载变量关联的数据源（逐个查询，变量数量少不会影响性能） */
    private Map<Long, Datasource> batchLoadDatasources(List<FlowVariable> variables) {
        return variables.stream()
                .map(FlowVariable::getDatasourceId)
                .filter(id -> id != null)
                .distinct()
                .map(datasourceMapper::selectById)
                .filter(ds -> ds != null)
                .collect(Collectors.toMap(Datasource::getId, ds -> ds, (a, b) -> a));
    }

    /** 批量加载变量关联的节点 */
    private Map<Long, FlowNode> batchLoadNodes(List<FlowVariable> variables) {
        Set<Long> ids = variables.stream()
                .map(FlowVariable::getNodeId)
                .filter(id -> id != null)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) return Map.of();
        return flowNodeMapper.selectByIds(ids).stream()
                .collect(Collectors.toMap(FlowNode::getId, fn -> fn));
    }

    /** 内置变量始终自动注入：_input/_fileStream/_fileName 来自节点结果，_env/_env_code 来自执行环境 */
    private Map<String, Object> autoInjectFlowParam(Map<String, NodeResult> previousResults,
                                                      Map<String, Object> result) {
        // _env/_env_code 取自执行环境上下文，与节点结果无关，故不放在下面的 null 判断内
        // （ApiConfigController 等调用方会传 null 的 previousResults）
        // 此处刻意不抛异常：两个调用方 catch 后会整体降级（RefApiNodeExecutor → Map.of()、
        // ApiConfigController → null），抛错会让整个变量池消失，把无关变量误报成"全局参数缺失"。
        // 环境未设置时的报错交给真正取值的那一刻（ParamResolver → BuiltinVariable.resolve）。
        Long envId = EnvironmentContext.getEnvId();
        if (envId != null) {
            result.put(BuiltinVariable.ENV_ID, envId);
            result.put(BuiltinVariable.ENV_CODE, EnvironmentContext.getEnvCode());
        } else {
            log.debug("当前执行未设置环境，跳过内置变量注入: {} / {}",
                    BuiltinVariable.ENV_ID, BuiltinVariable.ENV_CODE);
        }

        if (previousResults != null) {
            // 自动注入 _input、_fileStream、_fileName（无需在变量池中配置）
            for (String key : new String[]{
                    com.arbor.engine.executor.StartNodeExecutor.FLOW_PARAM_KEY,
                    "_fileStream", "_fileName"}) {
                NodeResult nr = previousResults.get(key);
                if (nr != null && nr.isSuccess() && nr.getData() != null) {
                    result.put(key, nr.getData());
                }
            }
        }
        return result;
    }
}
