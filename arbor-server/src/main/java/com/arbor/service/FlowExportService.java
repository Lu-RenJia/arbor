package com.arbor.service;

import com.arbor.common.AssertUtils;
import com.arbor.common.BusinessException;
import com.arbor.common.ErrorCode;
import com.arbor.dto.ImportExportDTO.*;
import com.arbor.engine.BuiltinVariable;
import com.arbor.entity.*;
import com.arbor.mapper.FlowNodeMapper;
import com.arbor.mapper.FlowVariableMapper;
import com.arbor.mapper.GlobalParamMapper;
import com.arbor.mapper.ImportExportMapper;
import com.arbor.mapper.RedisKeyPrefixMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 流程导出服务（从 ImportExportService 拆出）
 * <p>
 * 核心机制：
 * - 跨环境识别依赖各表 uid 列（UUID），运行时关联仍用自增 id
 * - 导出时把节点 config 内的资源 id 改写为 uid / 数据源名称，导入时由
 *   {@link FlowImportService} 映射回本地 id
 * - 全局参数只导定义不导值；数据源只导名称提示不导连接信息
 * </p>
 *
 * @author gwl
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlowExportService {

    private final ImportExportMapper importExportMapper;
    private final FlowNodeMapper flowNodeMapper;
    private final FlowVariableMapper flowVariableMapper;
    private final GlobalParamMapper globalParamMapper;
    private final RedisKeyPrefixMapper redisKeyPrefixMapper;
    private final ObjectMapper objectMapper;
    private final EnvironmentService environmentService;

    /** ${xxx} 占位符（与 ParamResolver 保持一致），用于提取全局参数引用 */
    static final Pattern PARAM_REF_PATTERN =
            Pattern.compile("\\$\\{([a-zA-Z_]\\w*)");

    /** 形似全局参数引用但实为引擎内置语法的名称，扫描时排除 */
    static final Set<String> PARAM_REF_EXCLUDES = BuiltinVariable.ALL;

    /** 导出流程（递归包含子流程与被引用的共享/内联资源） */
    public String exportFlows(List<Long> flowIds) {
        AssertUtils.isTrue(flowIds != null && !flowIds.isEmpty(), ErrorCode.IMPORT_PACKAGE_INVALID, "flowIds 为空");
        ExportCtx ctx = new ExportCtx();
        for (Long flowId : flowIds) {
            exportFlow(ctx, flowId);
        }
        scanParamRefs(ctx);
        return serialize(ctx.pkg);
    }

    /** 导出应用（连带其流程及全部依赖） */
    public String exportApps(List<Long> appIds) {
        AssertUtils.isTrue(appIds != null && !appIds.isEmpty(), ErrorCode.IMPORT_PACKAGE_INVALID, "appIds 为空");
        ExportCtx ctx = new ExportCtx();
        for (Long appId : appIds) {
            Application app = importExportMapper.selectAppById(appId);
            AssertUtils.notNull(app, ErrorCode.EXPORT_TARGET_NOT_FOUND);
            AppPack ap = new AppPack();
            ap.setUid(app.getUid());
            ap.setName(app.getName());
            ap.setInputParams(app.getInputParams());
            ap.setRemark(app.getRemark());
            if (app.getFlowId() != null) {
                Flow flow = importExportMapper.selectFlowById(app.getFlowId());
                if (flow != null) {
                    exportFlow(ctx, flow.getId());
                    ap.setFlowUid(flow.getUid());
                }
            }
            if (app.getEnvId() != null) {
                Environment env = environmentService.getByIdCached(app.getEnvId());
                if (env != null) ap.setEnvCode(env.getCode());
            }
            ctx.pkg.getApplications().add(ap);
        }
        scanParamRefs(ctx);
        return serialize(ctx.pkg);
    }

    /** 导出上下文（收集过程中的去重与提示） */
    private static class ExportCtx {
        final TransferPackage pkg = new TransferPackage();
        final Set<Long> visitedFlows = new HashSet<>();
        final Set<Long> globalDbScripts = new HashSet<>();
        final Set<Long> globalProgramScripts = new HashSet<>();
        final Set<Long> globalApiConfigs = new HashSet<>();
        final Set<Long> globalRedisScripts = new HashSet<>();
        final Set<String> paramNames = new LinkedHashSet<>();
        final Map<Long, String> dsNameById = new HashMap<>();
        final Map<Long, String> redisDsNameById = new HashMap<>();
    }

    private void exportFlow(ExportCtx ctx, Long flowId) {
        if (!ctx.visitedFlows.add(flowId)) return;
        Flow flow = importExportMapper.selectFlowById(flowId);
        AssertUtils.notNull(flow, ErrorCode.EXPORT_TARGET_NOT_FOUND);

        FlowPack pack = new FlowPack();
        pack.setUid(flow.getUid());
        pack.setName(flow.getName());
        pack.setDescription(flow.getDescription());
        pack.setStatus(flow.getStatus());

        List<FlowNode> nodes = flowNodeMapper.selectByFlowId(flowId);
        Map<Long, String> nodeCodeById = new HashMap<>();
        for (FlowNode n : nodes) nodeCodeById.put(n.getId(), n.getNodeCode());
        for (FlowNode n : nodes) {
            NodePack np = new NodePack();
            np.setNodeCode(n.getNodeCode());
            np.setNodeName(n.getNodeName());
            np.setNodeType(n.getNodeType());
            np.setSortOrder(n.getSortOrder());
            np.setExampleOutput(n.getExampleOutput());
            np.setBranchOfCode(n.getBranchOf() != null ? nodeCodeById.get(n.getBranchOf()) : null);
            np.setConfig(rewriteConfigForExport(ctx, n.getNodeType(), n.getConfig()));
            pack.getNodes().add(np);
        }

        for (FlowVariable v : flowVariableMapper.selectByFlowId(flowId)) {
            VarPack vp = new VarPack();
            vp.setVarName(v.getVarName());
            if (v.getParamId() != null) {
                GlobalParam p = globalParamMapper.selectById(v.getParamId());
                if (p != null) {
                    vp.setParamName(p.getParamName());
                    addParamPack(ctx, p.getParamName(), p.getRemark());
                }
            }
            vp.setNodeCode(v.getNodeCode());
            if (v.getDatasourceId() != null) vp.setDatasourceName(datasourceName(ctx, v.getDatasourceId()));
            pack.getVariables().add(vp);
        }

        // 内联资源（flow_id 归属本流程）
        for (DbScript s : importExportMapper.selectDbScriptsByFlowId(flowId)) {
            pack.getDbScripts().add(toDbScriptPack(ctx, s));
        }
        for (ProgramScript s : importExportMapper.selectProgramScriptsByFlowId(flowId)) {
            pack.getProgramScripts().add(toProgramScriptPack(s));
        }
        for (ApiConfig s : importExportMapper.selectApiConfigsByFlowId(flowId)) {
            pack.getApiConfigs().add(toApiConfigPack(s));
        }
        for (RedisScript s : importExportMapper.selectRedisScriptsByFlowId(flowId)) {
            pack.getRedisScripts().add(toRedisScriptPack(ctx, s));
        }

        ctx.pkg.getFlows().add(pack);
    }

    /** 导出时改写节点 config：资源 id → uid / 数据源名称 */
    private String rewriteConfigForExport(ExportCtx ctx, String nodeType, String config) {
        if (config == null || config.isBlank() || nodeType == null) return config;
        Map<String, Object> map;
        try {
            map = objectMapper.readValue(config, Map.class);
        } catch (Exception e) {
            return config;
        }
        switch (nodeType) {
            case "DB_QUERY" -> {
                Long dsId = toLong(map.get("datasourceId"));
                if (dsId != null) map.put("datasourceId", datasourceName(ctx, dsId));
            }
            case "REF_DB_SCRIPT" -> {
                Long id = toLong(map.get("dbScriptId"));
                if (id != null) {
                    DbScript s = importExportMapper.selectDbScriptById(id);
                    if (s != null) {
                        collectDbScript(ctx, s);
                        map.put("dbScriptId", s.getUid());
                    }
                }
            }
            case "REF_API" -> {
                Long id = toLong(map.get("apiConfigId"));
                if (id != null) {
                    ApiConfig s = importExportMapper.selectApiConfigById(id);
                    if (s != null) {
                        collectApiConfig(ctx, s);
                        map.put("apiConfigId", s.getUid());
                    }
                }
            }
            case "REF_PROGRAM_SCRIPT" -> {
                // 程序节点 config 中存的是 scriptId 业务字符串（非数字 id），兼容两种形态
                ProgramScript s = null;
                Long psId = toLong(map.get("programScriptId"));
                if (psId != null) {
                    s = importExportMapper.selectProgramScriptById(psId);
                } else if (map.get("programScriptId") instanceof String scriptId && !scriptId.isBlank()) {
                    s = importExportMapper.selectProgramScriptByScriptId(scriptId);
                }
                if (s != null) {
                    collectProgramScript(ctx, s);
                    map.put("programScriptId", s.getUid());
                }
            }
            case "REDIS" -> {
                Long id = toLong(map.get("redisConfigId"));
                if (id != null) {
                    RedisScript s = importExportMapper.selectRedisScriptById(id);
                    if (s != null) {
                        collectRedisScript(ctx, s);
                        map.put("redisConfigId", s.getUid());
                    }
                }
            }
            case "REF_FLOW" -> {
                Long id = toLong(map.get("flowId"));
                if (id != null) {
                    Flow sub = importExportMapper.selectFlowById(id);
                    if (sub != null) {
                        exportFlow(ctx, sub.getId());
                        map.put("flowId", sub.getUid());
                    }
                }
            }
            default -> {
            }
        }
        try {
            return objectMapper.writeValueAsString(map);
        } catch (Exception e) {
            return config;
        }
    }

    private void collectDbScript(ExportCtx ctx, DbScript s) {
        if (s.getFlowId() != null) {
            exportFlow(ctx, s.getFlowId()); // 内联资源随属主流程导出
            return;
        }
        if (ctx.globalDbScripts.add(s.getId())) {
            ctx.pkg.getResources().getDbScripts().add(toDbScriptPack(ctx, s));
        }
    }

    private void collectProgramScript(ExportCtx ctx, ProgramScript s) {
        if (s.getFlowId() != null) {
            exportFlow(ctx, s.getFlowId());
            return;
        }
        if (ctx.globalProgramScripts.add(s.getId())) {
            ctx.pkg.getResources().getProgramScripts().add(toProgramScriptPack(s));
        }
    }

    private void collectApiConfig(ExportCtx ctx, ApiConfig s) {
        if (s.getFlowId() != null) {
            exportFlow(ctx, s.getFlowId());
            return;
        }
        if (ctx.globalApiConfigs.add(s.getId())) {
            ctx.pkg.getResources().getApiConfigs().add(toApiConfigPack(s));
        }
    }

    private void collectRedisScript(ExportCtx ctx, RedisScript s) {
        if (s.getFlowId() != null) {
            exportFlow(ctx, s.getFlowId()); // 内联配置随属主流程导出
            return;
        }
        if (ctx.globalRedisScripts.add(s.getId())) {
            ctx.pkg.getResources().getRedisScripts().add(toRedisScriptPack(ctx, s));
        }
    }

    private DbScriptPack toDbScriptPack(ExportCtx ctx, DbScript s) {
        DbScriptPack p = new DbScriptPack();
        p.setUid(s.getUid());
        p.setName(s.getName());
        p.setSqlContent(s.getSqlContent());
        p.setReturnLimit(s.getReturnLimit());
        p.setQueryTimeout(s.getQueryTimeout());
        p.setRemark(s.getRemark());
        if (s.getDatasourceId() != null) p.setDatasourceName(datasourceName(ctx, s.getDatasourceId()));
        return p;
    }

    private ProgramScriptPack toProgramScriptPack(ProgramScript s) {
        ProgramScriptPack p = new ProgramScriptPack();
        p.setUid(s.getUid());
        p.setScriptId(s.getScriptId());
        p.setName(s.getName());
        p.setScriptContent(s.getScriptContent());
        p.setQueryTimeout(s.getQueryTimeout());
        p.setRemark(s.getRemark());
        return p;
    }

    private ApiConfigPack toApiConfigPack(ApiConfig s) {
        ApiConfigPack p = new ApiConfigPack();
        p.setUid(s.getUid());
        p.setName(s.getName());
        p.setMethod(s.getMethod());
        p.setUrl(s.getUrl());
        p.setTimeout(s.getTimeout());
        p.setHeaders(s.getHeaders());
        p.setParams(s.getParams());
        p.setBodyType(s.getBodyType());
        p.setBodyContent(s.getBodyContent());
        p.setRemark(s.getRemark());
        return p;
    }

    private RedisScriptPack toRedisScriptPack(ExportCtx ctx, RedisScript s) {
        RedisScriptPack p = new RedisScriptPack();
        p.setUid(s.getUid());
        p.setName(s.getName());
        p.setCommand(s.getCommand());
        p.setRedisKey(s.getRedisKey());
        p.setRedisValue(s.getRedisValue());
        p.setTtl(s.getTtl());
        p.setTtlUnit(s.getTtlUnit());
        p.setRemark(s.getRemark());
        if (s.getDatasourceId() != null) {
            String name = ctx.redisDsNameById.computeIfAbsent(s.getDatasourceId(), id -> {
                RedisDatasource ds = importExportMapper.selectRedisDatasourceById(id);
                if (ds == null) return null;
                DsHint hint = new DsHint();
                hint.setName(ds.getName());
                ctx.pkg.getRedisDatasources().add(hint);
                return ds.getName();
            });
            p.setDatasourceName(name);
        }
        // 前缀按「值」导出（与数据源同口径，不用环境相关的自增 id），导入时在目标数据源下查找或补建
        if (s.getPrefixId() != null) {
            RedisKeyPrefix prefix = redisKeyPrefixMapper.selectById(s.getPrefixId());
            if (prefix != null) {
                p.setPrefixValue(prefix.getPrefix());
                p.setPrefixName(prefix.getName());
            }
        }
        return p;
    }

    /** 数据源 id → 名称（同时登记名称提示） */
    private String datasourceName(ExportCtx ctx, Long dsId) {
        return ctx.dsNameById.computeIfAbsent(dsId, id -> {
            Datasource ds = importExportMapper.selectDatasourceById(id);
            if (ds == null) return null;
            DsHint hint = new DsHint();
            hint.setName(ds.getName());
            hint.setDbType(ds.getDbType());
            ctx.pkg.getDatasources().add(hint);
            return ds.getName();
        });
    }

    private void addParamPack(ExportCtx ctx, String paramName, String remark) {
        if (paramName != null && ctx.paramNames.add(paramName)) {
            ParamPack p = new ParamPack();
            p.setParamName(paramName);
            p.setRemark(remark);
            ctx.pkg.getGlobalParams().add(p);
        }
    }

    /** 扫描包内所有文本中的 ${参数名} 引用，补全全局参数定义 */
    private void scanParamRefs(ExportCtx ctx) {
        Set<String> texts = new LinkedHashSet<>();
        for (FlowPack fp : ctx.pkg.getFlows()) {
            for (NodePack np : fp.getNodes()) texts.add(np.getConfig());
            for (DbScriptPack p : fp.getDbScripts()) texts.add(p.getSqlContent());
            for (ProgramScriptPack p : fp.getProgramScripts()) texts.add(p.getScriptContent());
            for (ApiConfigPack p : fp.getApiConfigs()) {
                texts.add(p.getUrl());
                texts.add(p.getHeaders());
                texts.add(p.getParams());
                texts.add(p.getBodyContent());
            }
        }
        ResourcePack rp = ctx.pkg.getResources();
        for (DbScriptPack p : rp.getDbScripts()) texts.add(p.getSqlContent());
        for (ProgramScriptPack p : rp.getProgramScripts()) texts.add(p.getScriptContent());
        for (ApiConfigPack p : rp.getApiConfigs()) {
            texts.add(p.getUrl());
            texts.add(p.getHeaders());
            texts.add(p.getParams());
            texts.add(p.getBodyContent());
        }
        for (RedisScriptPack p : rp.getRedisScripts()) {
            texts.add(p.getRedisKey());
            texts.add(p.getRedisValue());
        }
        for (String text : texts) {
            if (text == null || text.isBlank()) continue;
            Matcher m = PARAM_REF_PATTERN.matcher(text);
            while (m.find()) {
                String name = m.group(1);
                if (PARAM_REF_EXCLUDES.contains(name)) continue;
                if (ctx.paramNames.contains(name)) continue;
                GlobalParam p = globalParamMapper.selectByParamName(name);
                if (p != null) addParamPack(ctx, p.getParamName(), p.getRemark());
            }
        }
    }

    private String serialize(TransferPackage pkg) {
        pkg.setExportedAt(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(pkg);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR);
        }
    }

    private Long toLong(Object v) {
        if (v instanceof Number n) return n.longValue();
        if (v instanceof String s && !s.isBlank()) {
            try {
                return Long.parseLong(s);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }
}
