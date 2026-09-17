package com.arbor.service;

import com.arbor.common.AssertUtils;
import com.arbor.common.ErrorCode;
import com.arbor.entity.*;
import com.arbor.mapper.*;
import com.arbor.vo.ResourceTraceVO;
import com.arbor.vo.ResourceTraceVO.TraceEdge;
import com.arbor.vo.ResourceTraceVO.TraceNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 引用追踪服务：分析资源被哪些资源直接/间接引用，构建有向图数据
 *
 * @author gwl
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResourceTraceService {

    private final GlobalParamMapper globalParamMapper;
    private final DatasourceMapper datasourceMapper;
    private final DbScriptMapper dbScriptMapper;
    private final ProgramScriptMapper programScriptMapper;
    private final ApiConfigMapper apiConfigMapper;
    private final RedisDatasourceMapper redisDatasourceMapper;
    private final RedisScriptMapper redisScriptMapper;
    private final FlowMapper flowMapper;
    private final FlowNodeMapper flowNodeMapper;
    private final FlowVariableMapper flowVariableMapper;
    private final ApplicationMapper applicationMapper;

    /**
     * 追踪指定资源的引用链路
     *
     * @param type 资源类型: global_param/datasource/db_script/program_script/api_config/redis_datasource/redis_script/flow
     * @param id   资源ID
     */
    public ResourceTraceVO trace(String type, Long id) {
        var nodes = new java.util.ArrayList<TraceNode>();
        var edges = new java.util.ArrayList<TraceEdge>();
        Set<String> visited = new HashSet<>();

        String rootId = type + ":" + id;
        String rootName = resolveRootName(type, id);

        nodes.add(TraceNode.builder()
                .id(rootId).label(rootName).type(type)
                .resourceId(String.valueOf(id)).relation("起始资源").level(0)
                .build());
        visited.add(rootId);

        switch (type) {
            case "global_param"     -> traceGlobalParam(id, rootId, nodes, edges, visited);
            case "datasource"       -> traceDatasource(id, rootId, nodes, edges, visited);
            case "db_script"        -> traceDbScript(id, rootId, nodes, edges, visited);
            case "program_script"   -> traceProgramScript(id, rootId, nodes, edges, visited);
            case "api_config"       -> traceApiConfig(id, rootId, nodes, edges, visited);
            case "redis_datasource" -> traceRedisDatasource(id, rootId, nodes, edges, visited);
            case "redis_script"     -> traceRedisScript(id, rootId, nodes, edges, visited);
            case "flow"             -> traceFlow(id, rootId, nodes, edges, visited, 1);
            default -> throw new IllegalArgumentException("不支持的资源类型: " + type);
        }

        log.info("引用追踪: type={}, id={}, 节点数={}, 边数={}", type, id, nodes.size(), edges.size());
        return ResourceTraceVO.builder()
                .nodes(nodes).edges(edges)
                .rootName(rootName).rootType(type)
                .build();
    }

    // ====== 各资源类型追踪实现 ======

    /** 全局参数：扫描所有资源中的 ${paramName} 占位符 + 变量池直接引用 */
    private void traceGlobalParam(Long id, String rootId,
                                  List<TraceNode> nodes, List<TraceEdge> edges, Set<String> visited) {
        GlobalParam param = globalParamMapper.selectById(id);
        AssertUtils.notNull(param, ErrorCode.PARAM_NOT_FOUND);
        String placeholder = "${" + param.getParamName() + "}";

        // 扫描数据源（host/databaseName/username/password 字段）
        for (Datasource ds : datasourceMapper.selectAll()) {
            String field = findParamField(placeholder, ds.getHost(), ds.getDatabaseName(), ds.getUsername(), ds.getPassword());
            if (field != null) {
                String nid = "datasource:" + ds.getId();
                if (visited.add(nid)) {
                    nodes.add(node(nid, ds.getName(), "datasource", String.valueOf(ds.getId()), field + "引用", 1));
                    edges.add(edge(rootId, nid, field));
                    traceDatasourceUp(ds.getId(), nid, nodes, edges, visited, 2);
                }
            }
        }

        // 扫描数据库脚本（sql_content 字段）
        for (DbScript s : dbScriptMapper.selectAll()) {
            if (contains(s.getSqlContent(), placeholder)) {
                String nid = "db_script:" + s.getId();
                if (visited.add(nid)) {
                    nodes.add(node(nid, s.getName(), "db_script", String.valueOf(s.getId()), "SQL内容引用", 1));
                    edges.add(edge(rootId, nid, "SQL引用"));
                    traceDbScriptUp(s.getId(), nid, nodes, edges, visited, 2);
                }
            }
        }

        // 扫描程序脚本（script_content 字段）
        for (ProgramScript s : programScriptMapper.selectAll()) {
            if (contains(s.getScriptContent(), placeholder)) {
                String nid = "program_script:" + s.getId();
                if (visited.add(nid)) {
                    nodes.add(node(nid, s.getName(), "program_script", s.getScriptId(), "脚本内容引用", 1));
                    edges.add(edge(rootId, nid, "脚本引用"));
                    traceProgramScriptUp(s.getScriptId(), nid, nodes, edges, visited, 2);
                }
            }
        }

        // 扫描接口配置（url/bodyContent 字段）
        for (ApiConfig a : apiConfigMapper.selectAll()) {
            if (contains(a.getUrl(), placeholder) || contains(a.getBodyContent(), placeholder)) {
                String nid = "api_config:" + a.getId();
                if (visited.add(nid)) {
                    nodes.add(node(nid, a.getName(), "api_config", String.valueOf(a.getId()), "接口配置引用", 1));
                    edges.add(edge(rootId, nid, "接口引用"));
                    traceApiConfigUp(a.getId(), nid, nodes, edges, visited, 2);
                }
            }
        }

        // 扫描 Redis 脚本（redis_key/redis_value 字段）
        for (RedisScript r : redisScriptMapper.selectAll()) {
            if (contains(r.getRedisKey(), placeholder) || contains(r.getRedisValue(), placeholder)) {
                String nid = "redis_script:" + r.getId();
                if (visited.add(nid)) {
                    nodes.add(node(nid, r.getName(), "redis_script", String.valueOf(r.getId()), "Redis配置引用", 1));
                    edges.add(edge(rootId, nid, "Redis引用"));
                    traceRedisScriptUp(r.getId(), nid, nodes, edges, visited, 2);
                }
            }
        }

        // 流程变量池直接引用（flow_variable.param_id）
        for (FlowVariable v : flowVariableMapper.selectByParamId(id)) {
            String nid = "flow:" + v.getFlowId();
            if (visited.add(nid)) {
                Flow flow = flowMapper.selectById(v.getFlowId());
                String flowName = flow != null ? flow.getName() : "流程#" + v.getFlowId();
                nodes.add(node(nid, flowName, "flow", String.valueOf(v.getFlowId()), "变量池引用(" + v.getVarName() + ")", 1));
                edges.add(edge(rootId, nid, "变量池"));
                traceFlowUp(v.getFlowId(), nid, nodes, edges, visited, 2);
            }
        }
    }

    /** 数据源：DB脚本引用 + 变量池引用 + DB_QUERY节点引用 */
    private void traceDatasource(Long id, String rootId,
                                 List<TraceNode> nodes, List<TraceEdge> edges, Set<String> visited) {
        // db_script.datasource_id
        for (DbScript s : dbScriptMapper.selectByDatasourceId(id)) {
            String nid = "db_script:" + s.getId();
            if (visited.add(nid)) {
                nodes.add(node(nid, s.getName(), "db_script", String.valueOf(s.getId()), "绑定数据源", 1));
                edges.add(edge(rootId, nid, "绑定"));
                traceDbScriptUp(s.getId(), nid, nodes, edges, visited, 2);
            }
        }
        // flow_variable.datasource_id（SqlTool 注入）
        for (FlowVariable v : flowVariableMapper.selectByDatasourceId(id)) {
            addFlowViaVariable(v, rootId, "变量池引用(" + v.getVarName() + ")", nodes, edges, visited);
        }
        // DB_QUERY 节点 config.datasourceId
        traceFlowNodesUp(
                flowNodeMapper.selectByConfigRef("DB_QUERY", "datasourceId", String.valueOf(id)),
                rootId, nodes, edges, visited, 1);
    }

    /** 数据库脚本：REF_DB_SCRIPT 节点引用 */
    private void traceDbScript(Long id, String rootId,
                               List<TraceNode> nodes, List<TraceEdge> edges, Set<String> visited) {
        traceFlowNodesUp(
                flowNodeMapper.selectByConfigRef("REF_DB_SCRIPT", "dbScriptId", String.valueOf(id)),
                rootId, nodes, edges, visited, 1);
    }

    /** 程序脚本：REF_PROGRAM_SCRIPT 节点引用（config 中存的是 scriptId 字符串） */
    private void traceProgramScript(Long id, String rootId,
                                    List<TraceNode> nodes, List<TraceEdge> edges, Set<String> visited) {
        ProgramScript script = programScriptMapper.selectById(id);
        AssertUtils.notNull(script, ErrorCode.PROGRAM_SCRIPT_NOT_FOUND, id);
        traceFlowNodesUp(
                flowNodeMapper.selectByConfigRef("REF_PROGRAM_SCRIPT", "programScriptId", script.getScriptId()),
                rootId, nodes, edges, visited, 1);
    }

    /** 接口配置：REF_API 节点引用 */
    private void traceApiConfig(Long id, String rootId,
                                List<TraceNode> nodes, List<TraceEdge> edges, Set<String> visited) {
        traceFlowNodesUp(
                flowNodeMapper.selectByConfigRef("REF_API", "apiConfigId", String.valueOf(id)),
                rootId, nodes, edges, visited, 1);
    }

    /** Redis 数据源：redis_script.datasource_id */
    private void traceRedisDatasource(Long id, String rootId,
                                      List<TraceNode> nodes, List<TraceEdge> edges, Set<String> visited) {
        for (RedisScript r : redisScriptMapper.selectByDatasourceId(id)) {
            String nid = "redis_script:" + r.getId();
            if (visited.add(nid)) {
                nodes.add(node(nid, r.getName(), "redis_script", String.valueOf(r.getId()), "绑定Redis数据源", 1));
                edges.add(edge(rootId, nid, "绑定"));
                traceRedisScriptUp(r.getId(), nid, nodes, edges, visited, 2);
            }
        }
    }

    /** Redis 脚本：REDIS 节点 config.redisConfigId */
    private void traceRedisScript(Long id, String rootId,
                                  List<TraceNode> nodes, List<TraceEdge> edges, Set<String> visited) {
        traceFlowNodesUp(
                flowNodeMapper.selectByConfigRef("REDIS", "redisConfigId", String.valueOf(id)),
                rootId, nodes, edges, visited, 1);
    }

    /** 流程：应用引用 + 被其他流程通过 REF_FLOW 引用 */
    private void traceFlow(Long id, String rootId,
                           List<TraceNode> nodes, List<TraceEdge> edges, Set<String> visited, int level) {
        // 哪些应用使用了这个流程
        for (Application app : applicationMapper.selectByFlowId(id)) {
            String nid = "application:" + app.getId();
            if (visited.add(nid)) {
                nodes.add(node(nid, app.getName(), "application", String.valueOf(app.getId()), "应用引用", level));
                edges.add(edge(rootId, nid, "应用"));
            }
        }
        // 哪些流程通过 REF_FLOW 节点引用了这个流程
        for (FlowNode fn : flowNodeMapper.selectByConfigRef("REF_FLOW", "flowId", String.valueOf(id))) {
            String nid = "flow:" + fn.getFlowId();
            if (visited.add(nid)) {
                Flow parentFlow = flowMapper.selectById(fn.getFlowId());
                String flowName = parentFlow != null ? parentFlow.getName() : "流程#" + fn.getFlowId();
                nodes.add(node(nid, flowName, "flow", String.valueOf(fn.getFlowId()),
                        "被流程「" + fn.getNodeName() + "」引用", level));
                edges.add(edge(rootId, nid, "子流程引用"));
                // 递归向上追踪父流程（visited 防环）
                traceFlow(fn.getFlowId(), nid, nodes, edges, visited, level + 1);
            }
        }
    }

    // ====== 向上追踪辅助方法 ======

    /** 数据源向上：不继续向上（数据源是终点资源，只被脚本/节点引用） */
    private void traceDatasourceUp(Long dsId, String dsNodeId,
                                   List<TraceNode> nodes, List<TraceEdge> edges, Set<String> visited, int level) {
        // 数据源被 DB_QUERY 节点直接使用
        traceFlowNodesUp(
                flowNodeMapper.selectByConfigRef("DB_QUERY", "datasourceId", String.valueOf(dsId)),
                dsNodeId, nodes, edges, visited, level);
    }

    /** DB脚本向上：找 REF_DB_SCRIPT 节点 → 流程 → 应用 */
    private void traceDbScriptUp(Long scriptId, String scriptNodeId,
                                 List<TraceNode> nodes, List<TraceEdge> edges, Set<String> visited, int level) {
        traceFlowNodesUp(
                flowNodeMapper.selectByConfigRef("REF_DB_SCRIPT", "dbScriptId", String.valueOf(scriptId)),
                scriptNodeId, nodes, edges, visited, level);
    }

    /** 程序脚本向上：找 REF_PROGRAM_SCRIPT 节点 → 流程 → 应用 */
    private void traceProgramScriptUp(String scriptId, String scriptNodeId,
                                      List<TraceNode> nodes, List<TraceEdge> edges, Set<String> visited, int level) {
        traceFlowNodesUp(
                flowNodeMapper.selectByConfigRef("REF_PROGRAM_SCRIPT", "programScriptId", scriptId),
                scriptNodeId, nodes, edges, visited, level);
    }

    /** 接口配置向上：找 REF_API 节点 → 流程 → 应用 */
    private void traceApiConfigUp(Long apiId, String apiNodeId,
                                  List<TraceNode> nodes, List<TraceEdge> edges, Set<String> visited, int level) {
        traceFlowNodesUp(
                flowNodeMapper.selectByConfigRef("REF_API", "apiConfigId", String.valueOf(apiId)),
                apiNodeId, nodes, edges, visited, level);
    }

    /** Redis脚本向上：找 REDIS 节点 → 流程 → 应用 */
    private void traceRedisScriptUp(Long scriptId, String scriptNodeId,
                                    List<TraceNode> nodes, List<TraceEdge> edges, Set<String> visited, int level) {
        traceFlowNodesUp(
                flowNodeMapper.selectByConfigRef("REDIS", "redisConfigId", String.valueOf(scriptId)),
                scriptNodeId, nodes, edges, visited, level);
    }

    /** 批量将 flow_node 列表向上追踪到流程和应用 */
    private void traceFlowNodesUp(List<FlowNode> flowNodes, String parentNodeId,
                                  List<TraceNode> nodes, List<TraceEdge> edges, Set<String> visited, int level) {
        for (FlowNode fn : flowNodes) {
            traceFlowNodeUp(fn, parentNodeId, nodes, edges, visited, level);
        }
    }

    /** 单个 flow_node 向上：节点 → 所属流程 → 应用/父流程 */
    private void traceFlowNodeUp(FlowNode fn, String parentNodeId,
                                 List<TraceNode> nodes, List<TraceEdge> edges, Set<String> visited, int level) {
        String flowNodeId = "flow:" + fn.getFlowId();
        if (visited.add(flowNodeId)) {
            Flow flow = flowMapper.selectById(fn.getFlowId());
            String flowName = flow != null ? flow.getName() : "流程#" + fn.getFlowId();
            nodes.add(node(flowNodeId, flowName, "flow", String.valueOf(fn.getFlowId()),
                    "节点「" + fn.getNodeName() + "」(" + fn.getNodeType() + ")", level));
            edges.add(edge(parentNodeId, flowNodeId, fn.getNodeType()));
            // 继续向上：流程 → 应用 / 父流程
            traceFlowUp(fn.getFlowId(), flowNodeId, nodes, edges, visited, level + 1);
        }
    }

    /** 流程向上：应用 + REF_FLOW 父流程 */
    private void traceFlowUp(Long flowId, String flowNodeId,
                             List<TraceNode> nodes, List<TraceEdge> edges, Set<String> visited, int level) {
        traceFlow(flowId, flowNodeId, nodes, edges, visited, level);
    }

    /** 通过流程变量池添加流程节点（用于数据源/全局参数的变量池引用） */
    private void addFlowViaVariable(FlowVariable v, String parentNodeId, String relation,
                                    List<TraceNode> nodes, List<TraceEdge> edges, Set<String> visited) {
        String nid = "flow:" + v.getFlowId();
        if (visited.add(nid)) {
            Flow flow = flowMapper.selectById(v.getFlowId());
            String flowName = flow != null ? flow.getName() : "流程#" + v.getFlowId();
            nodes.add(node(nid, flowName, "flow", String.valueOf(v.getFlowId()), relation, 1));
            edges.add(edge(parentNodeId, nid, "变量池"));
            traceFlowUp(v.getFlowId(), nid, nodes, edges, visited, 2);
        }
    }

    // ====== 工具方法 ======

    /** 获取资源显示名称 */
    private String resolveRootName(String type, Long id) {
        return switch (type) {
            case "global_param" -> {
                var p = globalParamMapper.selectById(id);
                yield p != null ? p.getParamName() : "全局参数#" + id;
            }
            case "datasource" -> {
                var d = datasourceMapper.selectById(id);
                yield d != null ? d.getName() : "数据源#" + id;
            }
            case "db_script" -> {
                var s = dbScriptMapper.selectById(id);
                yield s != null ? s.getName() : "数据库脚本#" + id;
            }
            case "program_script" -> {
                var s = programScriptMapper.selectById(id);
                yield s != null ? s.getName() : "程序脚本#" + id;
            }
            case "api_config" -> {
                var a = apiConfigMapper.selectById(id);
                yield a != null ? a.getName() : "接口配置#" + id;
            }
            case "redis_datasource" -> {
                var r = redisDatasourceMapper.selectById(id);
                yield r != null ? r.getName() : "Redis数据源#" + id;
            }
            case "redis_script" -> {
                var r = redisScriptMapper.selectById(id);
                yield r != null ? r.getName() : "Redis脚本#" + id;
            }
            case "flow" -> {
                var f = flowMapper.selectById(id);
                yield f != null ? f.getName() : "流程#" + id;
            }
            default -> type + "#" + id;
        };
    }

    /** 检查数据源哪个字段引用了全局参数，返回字段名；未引用返回 null */
    private String findParamField(String placeholder, String host, String dbName, String username, String password) {
        if (contains(host, placeholder)) return "host";
        if (contains(dbName, placeholder)) return "数据库名";
        if (contains(username, placeholder)) return "用户名";
        if (contains(password, placeholder)) return "密码";
        return null;
    }

    private boolean contains(String content, String placeholder) {
        return content != null && content.contains(placeholder);
    }

    private TraceNode node(String id, String label, String type, String resourceId, String relation, int level) {
        return TraceNode.builder()
                .id(id).label(label).type(type)
                .resourceId(resourceId).relation(relation).level(level)
                .build();
    }

    private TraceEdge edge(String source, String target, String label) {
        return TraceEdge.builder().source(source).target(target).label(label).build();
    }
}
