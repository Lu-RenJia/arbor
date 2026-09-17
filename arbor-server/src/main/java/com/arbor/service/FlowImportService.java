package com.arbor.service;

import com.arbor.common.AssertUtils;
import com.arbor.common.BusinessException;
import com.arbor.common.ErrorCode;
import com.arbor.common.TenantContext;
import com.arbor.dto.ImportExportDTO.*;
import com.arbor.entity.*;
import com.arbor.engine.SubFlowExecutor;
import com.arbor.groovy.GroovyScriptEngine;
import com.arbor.mapper.FlowMapper;
import com.arbor.mapper.FlowNodeMapper;
import com.arbor.mapper.FlowVariableMapper;
import com.arbor.mapper.GlobalParamMapper;
import com.arbor.mapper.ImportExportMapper;
import com.arbor.mapper.RedisKeyPrefixMapper;
import com.arbor.util.RedisCacheUtil;
import com.arbor.vo.EnvironmentVO;
import com.arbor.vo.ImportPreviewVO;
import com.arbor.vo.ImportResultVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.regex.Matcher;

/**
 * 流程导入服务（从 ImportExportService 拆出）
 * <p>
 * 核心机制：
 * - 导入时把包内节点 config 的 uid / 数据源名称映射回本地 id
 * - 冲突默认跳过，可通过全局开关/单项覆盖改为 OVERWRITE
 * - 导入完成后按用户维度批量失效相关缓存（项目强依赖缓存）
 * </p>
 *
 * @author gwl
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlowImportService {

    private final ImportExportMapper importExportMapper;
    private final FlowMapper flowMapper;
    private final FlowNodeMapper flowNodeMapper;
    private final FlowVariableMapper flowVariableMapper;
    private final GlobalParamMapper globalParamMapper;
    private final RedisKeyPrefixMapper redisKeyPrefixMapper;
    private final ObjectMapper objectMapper;
    private final RedisCacheUtil redisCacheUtil;
    private final GlobalParamService globalParamService;
    private final EnvironmentService environmentService;
    private final GroovyScriptEngine groovyScriptEngine;

    private static final String SKIP = "SKIP";
    private static final String OVERWRITE = "OVERWRITE";

    /**
     * 导入旧包（本次前缀改造之前导出，不带 prefixValue）时补建的前缀值。
     * 与 V24 存量迁移同口径，保证老包导入后 Redis 配置仍能执行而不是全部被跳过。
     */
    private static final String DEFAULT_IMPORT_PREFIX = "arbor";

    // ==================== 导入预览 ====================

    public ImportPreviewVO preview(String packageJson) {
        TransferPackage pkg = parsePackage(packageJson);
        ImportPreviewVO vo = new ImportPreviewVO();

        for (FlowPack fp : pkg.getFlows()) {
            Flow existing = importExportMapper.selectFlowByUid(fp.getUid());
            vo.getItems().add(buildItem("FLOW", fp.getUid(), fp.getName(), existing != null,
                    existing != null ? existing.getName() : null, null));
            for (DbScriptPack p : fp.getDbScripts()) {
                DbScript ex = importExportMapper.selectDbScriptByUid(p.getUid());
                vo.getItems().add(buildItem("DB_SCRIPT", p.getUid(), p.getName(), ex != null,
                        ex != null ? ex.getName() : null, fp.getName()));
            }
            for (ProgramScriptPack p : fp.getProgramScripts()) {
                ProgramScript ex = importExportMapper.selectProgramScriptByUid(p.getUid());
                vo.getItems().add(buildItem("PROGRAM_SCRIPT", p.getUid(), p.getName(), ex != null,
                        ex != null ? ex.getName() : null, fp.getName()));
            }
            for (ApiConfigPack p : fp.getApiConfigs()) {
                ApiConfig ex = importExportMapper.selectApiConfigByUid(p.getUid());
                vo.getItems().add(buildItem("API_CONFIG", p.getUid(), p.getName(), ex != null,
                        ex != null ? ex.getName() : null, fp.getName()));
            }
            for (RedisScriptPack p : fp.getRedisScripts()) {
                RedisScript ex = importExportMapper.selectRedisScriptByUid(p.getUid());
                vo.getItems().add(buildItem("REDIS_SCRIPT", p.getUid(), p.getName(), ex != null,
                        ex != null ? ex.getName() : null, fp.getName()));
            }
        }
        for (AppPack ap : pkg.getApplications()) {
            Application existing = importExportMapper.selectAppByUid(ap.getUid());
            vo.getItems().add(buildItem("APP", ap.getUid(), ap.getName(), existing != null,
                    existing != null ? existing.getName() : null, null));
        }
        ResourcePack rp = pkg.getResources();
        for (DbScriptPack p : rp.getDbScripts()) {
            DbScript ex = importExportMapper.selectDbScriptByUid(p.getUid());
            vo.getItems().add(buildItem("DB_SCRIPT", p.getUid(), p.getName(), ex != null,
                    ex != null ? ex.getName() : null, null));
        }
        for (ProgramScriptPack p : rp.getProgramScripts()) {
            ProgramScript ex = importExportMapper.selectProgramScriptByUid(p.getUid());
            vo.getItems().add(buildItem("PROGRAM_SCRIPT", p.getUid(), p.getName(), ex != null,
                    ex != null ? ex.getName() : null, null));
        }
        for (ApiConfigPack p : rp.getApiConfigs()) {
            ApiConfig ex = importExportMapper.selectApiConfigByUid(p.getUid());
            vo.getItems().add(buildItem("API_CONFIG", p.getUid(), p.getName(), ex != null,
                    ex != null ? ex.getName() : null, null));
        }
        for (RedisScriptPack p : rp.getRedisScripts()) {
            RedisScript ex = importExportMapper.selectRedisScriptByUid(p.getUid());
            vo.getItems().add(buildItem("REDIS_SCRIPT", p.getUid(), p.getName(), ex != null,
                    ex != null ? ex.getName() : null, null));
        }
        for (ParamPack p : pkg.getGlobalParams()) {
            GlobalParam ex = globalParamMapper.selectByParamName(p.getParamName());
            vo.getItems().add(buildItem("GLOBAL_PARAM", p.getParamName(), p.getParamName(), ex != null,
                    ex != null ? ex.getParamName() : null, null));
        }

        // 目标环境缺失的数据源（目标库必须预先存在同名数据源）
        for (DsHint h : pkg.getDatasources()) {
            if (importExportMapper.selectDatasourceIdByName(h.getName()) == null) {
                vo.getMissingDatasources().add(h.getName());
            }
        }
        for (DsHint h : pkg.getRedisDatasources()) {
            if (importExportMapper.selectRedisDatasourceByName(h.getName()) == null) {
                vo.getMissingRedisDatasources().add(h.getName());
            }
        }

        // 目标环境缺失的全局参数（被 ${xxx} 引用但本地不存在，包内会一并导入的除外）
        Set<String> pkgParamNames = new HashSet<>();
        for (ParamPack p : pkg.getGlobalParams()) pkgParamNames.add(p.getParamName());
        Set<String> nodeCodes = new HashSet<>();
        for (FlowPack fp : pkg.getFlows()) {
            for (NodePack np : fp.getNodes()) nodeCodes.add(np.getNodeCode());
        }
        Set<String> refNames = new LinkedHashSet<>();
        collectParamRefNames(pkg, refNames);
        for (String name : refNames) {
            if (pkgParamNames.contains(name) || nodeCodes.contains(name)
                    || FlowExportService.PARAM_REF_EXCLUDES.contains(name)) continue;
            if (globalParamMapper.selectByParamName(name) == null) {
                vo.getMissingParams().add(name);
            }
        }

        for (ImportPreviewVO.PreviewItem item : vo.getItems()) {
            if (item.isConflict()) vo.setConflictCount(vo.getConflictCount() + 1);
            else vo.setNewCount(vo.getNewCount() + 1);
        }
        if (!vo.getMissingDatasources().isEmpty()) {
            vo.getWarnings().add("存在目标环境缺失的 MySQL 数据源，相关节点/脚本导入后数据源绑定为空，需先在数据源管理中创建同名数据源");
        }
        if (!vo.getMissingRedisDatasources().isEmpty()) {
            vo.getWarnings().add("存在目标环境缺失的 Redis 数据源，相关 Redis 脚本导入后数据源绑定为空");
        }
        if (!vo.getMissingParams().isEmpty()) {
            vo.getWarnings().add("存在目标环境缺失的全局参数，导入后请在全局参数管理中补建并配置各环境的值");
        }
        return vo;
    }

    private ImportPreviewVO.PreviewItem buildItem(String type, String uid, String name, boolean conflict,
                                                  String existingName, String ownerFlowName) {
        ImportPreviewVO.PreviewItem item = new ImportPreviewVO.PreviewItem();
        item.setType(type);
        item.setUid(uid);
        item.setName(name);
        item.setConflict(conflict);
        item.setExistingName(existingName);
        item.setOwnerFlowName(ownerFlowName);
        return item;
    }

    /** 提取包内所有文本中的 ${参数名} 引用 */
    private void collectParamRefNames(TransferPackage pkg, Set<String> out) {
        List<String> texts = new ArrayList<>();
        for (FlowPack fp : pkg.getFlows()) {
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
        ResourcePack rp = pkg.getResources();
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
            Matcher m = FlowExportService.PARAM_REF_PATTERN.matcher(text);
            while (m.find()) out.add(m.group(1));
        }
    }

    // ==================== 导入执行 ====================

    @Transactional(rollbackFor = Exception.class)
    public ImportResultVO execute(String packageJson, String defaultConflictAction, Map<String, String> overrides) {
        TransferPackage pkg = parsePackage(packageJson);
        ImportResultVO result = new ImportResultVO();
        Long userId = TenantContext.getUserId();
        String defAction = OVERWRITE.equalsIgnoreCase(defaultConflictAction) ? OVERWRITE : SKIP;

        // 1. 全局参数定义（只补建缺失的，冲突一律跳过；参数值不导入）
        Map<String, Long> paramIdByName = new HashMap<>();
        for (GlobalParam p : globalParamMapper.selectAll()) {
            paramIdByName.put(p.getParamName(), p.getId());
        }
        for (ParamPack pp : pkg.getGlobalParams()) {
            Long existId = paramIdByName.get(pp.getParamName());
            if (existId != null) {
                result.setSkipped(result.getSkipped() + 1);
                result.getDetails().add("全局参数「" + pp.getParamName() + "」已存在，跳过");
                continue;
            }
            GlobalParam np = new GlobalParam();
            np.setParamName(pp.getParamName());
            np.setRemark(pp.getRemark());
            globalParamMapper.insert(np);
            paramIdByName.put(pp.getParamName(), np.getId());
            result.setCreated(result.getCreated() + 1);
            result.getDetails().add("全局参数「" + pp.getParamName() + "」已创建（参数值需手工配置）");
        }

        // 2. 数据源名称 → 本地 id（不导入连接信息，缺失仅告警）
        Map<String, Long> dsIdByName = new HashMap<>();
        for (DsHint h : pkg.getDatasources()) {
            Datasource ds = importExportMapper.selectDatasourceIdByName(h.getName());
            if (ds != null) dsIdByName.put(h.getName(), ds.getId());
            else result.getWarnings().add("缺少同名 MySQL 数据源「" + h.getName() + "」，相关对象的数据源绑定为空");
        }
        Map<String, Long> redisDsIdByName = new HashMap<>();
        for (DsHint h : pkg.getRedisDatasources()) {
            RedisDatasource ds = importExportMapper.selectRedisDatasourceByName(h.getName());
            if (ds != null) redisDsIdByName.put(h.getName(), ds.getId());
            else result.getWarnings().add("缺少同名 Redis 数据源「" + h.getName() + "」，引用它的 Redis 配置将被跳过（无法确定业务前缀）");
        }

        // 3. 预填充：包内所有资源（含将被跳过流程的内联资源）已存在于目标环境的 uid→id，
        //    保证"跳过"的共享资源引用仍能正确解析
        Map<String, Long> dbScriptIdByUid = new HashMap<>();
        // 程序脚本特殊：节点 config 中存 scriptId 业务字符串（非数字 id），故映射 uid → scriptId
        Map<String, String> psScriptIdByUid = new HashMap<>();
        Map<String, Long> apiIdByUid = new HashMap<>();
        Map<String, Long> redisScriptIdByUid = new HashMap<>();
        for (FlowPack fp : pkg.getFlows()) {
            preMapResources(fp.getDbScripts(), fp.getProgramScripts(), fp.getApiConfigs(),
                    dbScriptIdByUid, psScriptIdByUid, apiIdByUid);
        }
        preMapResources(pkg.getResources().getDbScripts(), pkg.getResources().getProgramScripts(),
                pkg.getResources().getApiConfigs(), dbScriptIdByUid, psScriptIdByUid, apiIdByUid);
        for (RedisScriptPack p : pkg.getResources().getRedisScripts()) {
            RedisScript ex = importExportMapper.selectRedisScriptByUid(p.getUid());
            if (ex != null) redisScriptIdByUid.put(p.getUid(), ex.getId());
        }
        // 内联 Redis 配置也要预填：否则被跳过的流程里那些引用无法解析
        for (FlowPack fp : pkg.getFlows()) {
            for (RedisScriptPack p : fp.getRedisScripts()) {
                RedisScript ex = importExportMapper.selectRedisScriptByUid(p.getUid());
                if (ex != null) redisScriptIdByUid.put(p.getUid(), ex.getId());
            }
        }
        // 业务前缀解析缓存（dsId|prefixValue → 本地 prefixId），避免同一前缀被多个配置重复补建
        Map<String, Long> prefixIdCache = new HashMap<>();

        // 4. 共享资源导入（冲突默认跳过）
        for (DbScriptPack p : pkg.getResources().getDbScripts()) {
            importDbScript(p, null, dsIdByName, dbScriptIdByUid, overrides, defAction, result);
        }
        for (ProgramScriptPack p : pkg.getResources().getProgramScripts()) {
            importProgramScript(p, null, psScriptIdByUid, overrides, defAction, result);
        }
        for (ApiConfigPack p : pkg.getResources().getApiConfigs()) {
            importApiConfig(p, null, apiIdByUid, overrides, defAction, result);
        }
        for (RedisScriptPack p : pkg.getResources().getRedisScripts()) {
            importRedisScript(p, null, redisDsIdByName, prefixIdCache, redisScriptIdByUid, overrides, defAction, result);
        }

        // 5. 流程第一遍：创建/更新流程行，建立 flowUid → 本地 id 映射
        Map<String, Long> flowIdByUid = new LinkedHashMap<>();
        Set<Long> executedFlowIds = new LinkedHashSet<>();
        for (FlowPack fp : pkg.getFlows()) {
            Flow existing = importExportMapper.selectFlowByUid(fp.getUid());
            String action = resolveAction(overrides, fp.getUid(), existing != null, defAction);
            if (existing != null && SKIP.equals(action)) {
                flowIdByUid.put(fp.getUid(), existing.getId());
                result.setSkipped(result.getSkipped() + 1);
                result.getDetails().add("流程「" + fp.getName() + "」已存在，跳过");
                continue;
            }
            Flow flow = existing != null ? existing : new Flow();
            if (existing == null) {
                flow.setName(generateUniqueName(fp.getName(), userId));
                flow.setStatus(fp.getStatus() != null ? fp.getStatus() : 0);
                flow.setNodeCount(0);
                flow.setUid(fp.getUid());
                importExportMapper.insertFlow(flow);
                result.setCreated(result.getCreated() + 1);
                result.getDetails().add("流程「" + flow.getName() + "」已创建");
            } else {
                // 覆盖：名称不与其他流程冲突时同步为包内名称
                String name = fp.getName();
                if (countOtherFlowByName(name, userId, existing.getId()) > 0) {
                    result.getWarnings().add("流程「" + existing.getName() + "」覆盖时名称「" + name + "」已被占用，保留原名称");
                    name = existing.getName();
                }
                flow.setName(name);
                flow.setDescription(fp.getDescription());
                flow.setStatus(fp.getStatus() != null ? fp.getStatus() : existing.getStatus());
                flow.setNodeCount(0);
                importExportMapper.overwriteFlow(flow);
                result.setUpdated(result.getUpdated() + 1);
                result.getDetails().add("流程「" + name + "」已覆盖更新");
            }
            flowIdByUid.put(fp.getUid(), flow.getId());
            executedFlowIds.add(flow.getId());
        }

        // 6. 流程第二遍：内联资源 → 节点 → 变量（仅处理已创建/覆盖的流程）
        Map<Long, Map<String, Long>> nodeCodeToIdByFlow = new HashMap<>();
        for (FlowPack fp : pkg.getFlows()) {
            Long flowId = flowIdByUid.get(fp.getUid());
            if (flowId == null || !executedFlowIds.contains(flowId)) continue;

            for (DbScriptPack p : fp.getDbScripts()) {
                importDbScript(p, flowId, dsIdByName, dbScriptIdByUid, overrides, defAction, result);
            }
            for (ProgramScriptPack p : fp.getProgramScripts()) {
                importProgramScript(p, flowId, psScriptIdByUid, overrides, defAction, result);
            }
            for (ApiConfigPack p : fp.getApiConfigs()) {
                importApiConfig(p, flowId, apiIdByUid, overrides, defAction, result);
            }
            for (RedisScriptPack p : fp.getRedisScripts()) {
                importRedisScript(p, flowId, redisDsIdByName, prefixIdCache, redisScriptIdByUid,
                        overrides, defAction, result);
            }

            Map<String, Long> nodeIdByCode = importNodes(fp, flowId, dsIdByName, dbScriptIdByUid,
                    psScriptIdByUid, apiIdByUid, redisScriptIdByUid, flowIdByUid, result);
            nodeCodeToIdByFlow.put(flowId, nodeIdByCode);
            flowMapper.updateNodeCount(flowId, fp.getNodes().size());
            groovyScriptEngine.evictFlowClasses(flowId);

            importVariables(fp, flowId, paramIdByName, dsIdByName, nodeIdByCode);
        }

        // 7. 应用导入
        Map<String, Long> envIdByCode = new HashMap<>();
        for (EnvironmentVO env : environmentService.listActive()) {
            envIdByCode.put(env.getCode(), env.getId());
        }
        for (AppPack ap : pkg.getApplications()) {
            Application existing = importExportMapper.selectAppByUid(ap.getUid());
            String action = resolveAction(overrides, ap.getUid(), existing != null, defAction);
            if (existing != null && SKIP.equals(action)) {
                result.setSkipped(result.getSkipped() + 1);
                result.getDetails().add("应用「" + ap.getName() + "」已存在，跳过");
                continue;
            }
            Long flowId = ap.getFlowUid() != null ? flowIdByUid.get(ap.getFlowUid()) : null;
            if (ap.getFlowUid() != null && flowId == null) {
                result.getWarnings().add("应用「" + ap.getName() + "」关联的流程不在导入包中，流程绑定为空");
            }
            Long envId = ap.getEnvCode() != null ? envIdByCode.get(ap.getEnvCode()) : null;
            if (ap.getEnvCode() != null && envId == null) {
                result.getWarnings().add("应用「" + ap.getName() + "」的环境「" + ap.getEnvCode() + "」在目标环境不存在，已置为默认环境");
            }
            Application app = existing != null ? existing : new Application();
            app.setName(ap.getName());
            app.setFlowId(flowId);
            app.setEnvId(envId);
            app.setInputParams(ap.getInputParams());
            app.setRemark(ap.getRemark());
            if (existing == null) {
                app.setUid(ap.getUid());
                importExportMapper.insertApp(app);
                result.setCreated(result.getCreated() + 1);
                result.getDetails().add("应用「" + ap.getName() + "」已创建");
            } else {
                importExportMapper.overwriteApp(app);
                result.setUpdated(result.getUpdated() + 1);
                result.getDetails().add("应用「" + ap.getName() + "」已覆盖更新");
            }
        }

        // 8. 缓存失效（项目强依赖缓存，导入涉及的维度全部清理）
        evictCaches(userId, executedFlowIds);

        log.info("导入完成: created={}, updated={}, skipped={}, warnings={}",
                result.getCreated(), result.getUpdated(), result.getSkipped(), result.getWarnings().size());
        return result;
    }

    private void preMapResources(List<DbScriptPack> dbScripts, List<ProgramScriptPack> psList,
                                 List<ApiConfigPack> apiList, Map<String, Long> dbScriptIdByUid,
                                 Map<String, String> psScriptIdByUid, Map<String, Long> apiIdByUid) {
        for (DbScriptPack p : dbScripts) {
            DbScript ex = importExportMapper.selectDbScriptByUid(p.getUid());
            if (ex != null) dbScriptIdByUid.put(p.getUid(), ex.getId());
        }
        for (ProgramScriptPack p : psList) {
            ProgramScript ex = importExportMapper.selectProgramScriptByUid(p.getUid());
            if (ex != null) {
                psScriptIdByUid.put(p.getUid(), ex.getScriptId());
                // 旧包兼容：源 scriptId → 本地 scriptId
                if (p.getScriptId() != null) psScriptIdByUid.put(p.getScriptId(), ex.getScriptId());
            }
        }
        for (ApiConfigPack p : apiList) {
            ApiConfig ex = importExportMapper.selectApiConfigByUid(p.getUid());
            if (ex != null) apiIdByUid.put(p.getUid(), ex.getId());
        }
    }

    private void importDbScript(DbScriptPack p, Long ownerFlowId, Map<String, Long> dsIdByName,
                                Map<String, Long> dbScriptIdByUid, Map<String, String> overrides,
                                String defAction, ImportResultVO result) {
        DbScript existing = importExportMapper.selectDbScriptByUid(p.getUid());
        String action = resolveAction(overrides, p.getUid(), existing != null, defAction);
        Long dsId = p.getDatasourceName() != null ? dsIdByName.get(p.getDatasourceName()) : null;
        if (existing != null && SKIP.equals(action)) {
            dbScriptIdByUid.put(p.getUid(), existing.getId());
            result.setSkipped(result.getSkipped() + 1);
            result.getDetails().add("数据库脚本「" + p.getName() + "」已存在，跳过");
            return;
        }
        DbScript e = existing != null ? existing : new DbScript();
        e.setName(p.getName());
        e.setDatasourceId(dsId);
        e.setSqlContent(p.getSqlContent());
        e.setReturnLimit(p.getReturnLimit());
        e.setQueryTimeout(p.getQueryTimeout());
        e.setRemark(p.getRemark());
        e.setFlowId(ownerFlowId);
        if (existing == null) {
            e.setUid(p.getUid());
            importExportMapper.insertDbScript(e);
            result.setCreated(result.getCreated() + 1);
            result.getDetails().add("数据库脚本「" + p.getName() + "」已创建");
        } else {
            importExportMapper.overwriteDbScript(e);
            result.setUpdated(result.getUpdated() + 1);
            result.getDetails().add("数据库脚本「" + p.getName() + "」已覆盖更新");
        }
        dbScriptIdByUid.put(p.getUid(), e.getId());
    }

    private void importProgramScript(ProgramScriptPack p, Long ownerFlowId, Map<String, String> psScriptIdByUid,
                                     Map<String, String> overrides, String defAction, ImportResultVO result) {
        ProgramScript existing = importExportMapper.selectProgramScriptByUid(p.getUid());
        String action = resolveAction(overrides, p.getUid(), existing != null, defAction);
        if (existing != null && SKIP.equals(action)) {
            psScriptIdByUid.put(p.getUid(), existing.getScriptId());
            if (p.getScriptId() != null) psScriptIdByUid.put(p.getScriptId(), existing.getScriptId());
            result.setSkipped(result.getSkipped() + 1);
            result.getDetails().add("程序脚本「" + p.getName() + "」已存在，跳过");
            return;
        }
        ProgramScript e = existing != null ? existing : new ProgramScript();
        e.setName(p.getName());
        e.setScriptContent(p.getScriptContent());
        e.setQueryTimeout(p.getQueryTimeout());
        e.setRemark(p.getRemark());
        e.setFlowId(ownerFlowId);
        if (existing == null) {
            e.setScriptId("PS" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase());
            e.setUid(p.getUid());
            importExportMapper.insertProgramScript(e);
            result.setCreated(result.getCreated() + 1);
            result.getDetails().add("程序脚本「" + p.getName() + "」已创建");
        } else {
            importExportMapper.overwriteProgramScript(e);
            result.setUpdated(result.getUpdated() + 1);
            result.getDetails().add("程序脚本「" + p.getName() + "」已覆盖更新");
        }
        psScriptIdByUid.put(p.getUid(), e.getScriptId());
        if (p.getScriptId() != null) psScriptIdByUid.put(p.getScriptId(), e.getScriptId());
    }

    private void importApiConfig(ApiConfigPack p, Long ownerFlowId, Map<String, Long> apiIdByUid,
                                 Map<String, String> overrides, String defAction, ImportResultVO result) {
        ApiConfig existing = importExportMapper.selectApiConfigByUid(p.getUid());
        String action = resolveAction(overrides, p.getUid(), existing != null, defAction);
        if (existing != null && SKIP.equals(action)) {
            apiIdByUid.put(p.getUid(), existing.getId());
            result.setSkipped(result.getSkipped() + 1);
            result.getDetails().add("接口配置「" + p.getName() + "」已存在，跳过");
            return;
        }
        ApiConfig e = existing != null ? existing : new ApiConfig();
        e.setName(p.getName());
        e.setMethod(p.getMethod());
        e.setUrl(p.getUrl());
        e.setTimeout(p.getTimeout());
        e.setHeaders(p.getHeaders());
        e.setParams(p.getParams());
        e.setBodyType(p.getBodyType());
        e.setBodyContent(p.getBodyContent());
        e.setRemark(p.getRemark());
        e.setFlowId(ownerFlowId);
        if (existing == null) {
            e.setApiId("API" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase());
            e.setUid(p.getUid());
            importExportMapper.insertApiConfig(e);
            result.setCreated(result.getCreated() + 1);
            result.getDetails().add("接口配置「" + p.getName() + "」已创建");
        } else {
            importExportMapper.overwriteApiConfig(e);
            result.setUpdated(result.getUpdated() + 1);
            result.getDetails().add("接口配置「" + p.getName() + "」已覆盖更新");
        }
        apiIdByUid.put(p.getUid(), e.getId());
    }

    private void importRedisScript(RedisScriptPack p, Long ownerFlowId, Map<String, Long> redisDsIdByName,
                                   Map<String, Long> prefixIdCache, Map<String, Long> redisScriptIdByUid,
                                   Map<String, String> overrides, String defAction, ImportResultVO result) {
        RedisScript existing = importExportMapper.selectRedisScriptByUid(p.getUid());
        String action = resolveAction(overrides, p.getUid(), existing != null, defAction);
        Long dsId = p.getDatasourceName() != null ? redisDsIdByName.get(p.getDatasourceName()) : null;
        if (existing != null && SKIP.equals(action)) {
            redisScriptIdByUid.put(p.getUid(), existing.getId());
            result.setSkipped(result.getSkipped() + 1);
            result.getDetails().add("Redis脚本「" + p.getName() + "」已存在，跳过");
            return;
        }
        Long prefixId = resolvePrefixId(p, dsId, prefixIdCache, result);
        if (prefixId == null) {
            // prefix_id 是 NOT NULL（D7），没有数据源就无法登记前缀；
            // 与其插一条永远执行不了的脏数据，不如跳过并让节点引用报警
            result.setSkipped(result.getSkipped() + 1);
            result.getDetails().add("Redis脚本「" + p.getName() + "」已跳过：目标环境缺少 Redis 数据源「"
                    + p.getDatasourceName() + "」，无法确定业务前缀");
            return;
        }
        RedisScript e = existing != null ? existing : new RedisScript();
        e.setName(p.getName());
        e.setFlowId(ownerFlowId);
        e.setDatasourceId(dsId);
        e.setPrefixId(prefixId);
        e.setCommand(p.getCommand());
        e.setRedisKey(p.getRedisKey());
        e.setRedisValue(p.getRedisValue());
        e.setTtl(p.getTtl());
        e.setTtlUnit(p.getTtlUnit());
        e.setRemark(p.getRemark());
        if (existing == null) {
            e.setUid(p.getUid());
            importExportMapper.insertRedisScript(e);
            result.setCreated(result.getCreated() + 1);
            result.getDetails().add("Redis脚本「" + p.getName() + "」已创建");
        } else {
            importExportMapper.overwriteRedisScript(e);
            result.setUpdated(result.getUpdated() + 1);
            result.getDetails().add("Redis脚本「" + p.getName() + "」已覆盖更新");
        }
        redisScriptIdByUid.put(p.getUid(), e.getId());
    }

    /**
     * 解析业务前缀：在目标数据源下按前缀值查找，缺失则自动补登记。
     * <p>
     * 前缀不用 uid 匹配而用「数据源 + 前缀值」：它的唯一约束本就是 uk_redis_prefix_ds(datasource_id, prefix)，
     * 而数据源也是按名称匹配的，两边口径一致才不会归错层。
     * </p>
     *
     * @return 本地 prefixId；dsId 为 null 时返回 null（调用方据此跳过该配置）
     */
    private Long resolvePrefixId(RedisScriptPack p, Long dsId, Map<String, Long> prefixIdCache,
                                 ImportResultVO result) {
        if (dsId == null) return null;
        // 旧包不带前缀值 → 按 V24 迁移同口径补一个 arbor 默认前缀
        String value = p.getPrefixValue() != null && !p.getPrefixValue().isBlank()
                ? p.getPrefixValue() : DEFAULT_IMPORT_PREFIX;
        String cacheKey = dsId + "|" + value;
        Long cached = prefixIdCache.get(cacheKey);
        if (cached != null) return cached;

        RedisKeyPrefix prefix = redisKeyPrefixMapper.selectByDatasourceIdAndPrefix(dsId, value);
        if (prefix == null) {
            prefix = new RedisKeyPrefix();
            prefix.setDatasourceId(dsId);
            prefix.setPrefix(value);
            prefix.setName(p.getPrefixName() != null && !p.getPrefixName().isBlank() ? p.getPrefixName() : value);
            prefix.setRemark("导入自动登记");
            prefix.setUid(UUID.randomUUID().toString());
            redisKeyPrefixMapper.insert(prefix);
            result.getDetails().add("Redis 业务前缀「" + value + "」在目标环境不存在，已自动登记");
        }
        prefixIdCache.put(cacheKey, prefix.getId());
        return prefix.getId();
    }

    /** 导入节点：先内联资源后节点（节点 config 引用内联资源 uid），返回 nodeCode → id 映射 */
    private Map<String, Long> importNodes(FlowPack fp, Long flowId, Map<String, Long> dsIdByName,
                                          Map<String, Long> dbScriptIdByUid, Map<String, String> psScriptIdByUid,
                                          Map<String, Long> apiIdByUid, Map<String, Long> redisScriptIdByUid,
                                          Map<String, Long> flowIdByUid, ImportResultVO result) {
        flowNodeMapper.deleteByFlowId(flowId);
        List<FlowNode> mainNodes = new ArrayList<>();
        List<FlowNode> branchNodes = new ArrayList<>();
        Map<String, String> branchOfCodeByNodeCode = new HashMap<>();
        for (NodePack np : fp.getNodes()) {
            FlowNode n = new FlowNode();
            n.setFlowId(flowId);
            n.setNodeCode(np.getNodeCode());
            n.setNodeName(np.getNodeName());
            n.setNodeType(np.getNodeType());
            n.setSortOrder(np.getSortOrder());
            n.setExampleOutput(np.getExampleOutput());
            n.setConfig(rewriteConfigForImport(np, dsIdByName, dbScriptIdByUid, psScriptIdByUid,
                    apiIdByUid, redisScriptIdByUid, flowIdByUid, fp.getName(), result));
            if (np.getBranchOfCode() == null || np.getBranchOfCode().isBlank()) {
                mainNodes.add(n);
            } else {
                branchNodes.add(n);
                branchOfCodeByNodeCode.put(np.getNodeCode(), np.getBranchOfCode());
            }
        }
        Map<String, Long> nodeIdByCode = new HashMap<>();
        if (!mainNodes.isEmpty()) {
            flowNodeMapper.insertBatch(mainNodes);
            for (FlowNode n : mainNodes) nodeIdByCode.put(n.getNodeCode(), n.getId());
        }
        if (!branchNodes.isEmpty()) {
            for (FlowNode n : branchNodes) {
                n.setBranchOf(nodeIdByCode.get(branchOfCodeByNodeCode.get(n.getNodeCode())));
            }
            flowNodeMapper.insertBatch(branchNodes);
            for (FlowNode n : branchNodes) nodeIdByCode.put(n.getNodeCode(), n.getId());
        }
        return nodeIdByCode;
    }

    /** 导入时改写节点 config：uid / 数据源名称 → 本地 id */
    private String rewriteConfigForImport(NodePack np, Map<String, Long> dsIdByName,
                                          Map<String, Long> dbScriptIdByUid, Map<String, String> psScriptIdByUid,
                                          Map<String, Long> apiIdByUid, Map<String, Long> redisScriptIdByUid,
                                          Map<String, Long> flowIdByUid, String flowName, ImportResultVO result) {
        String config = np.getConfig();
        if (config == null || config.isBlank() || np.getNodeType() == null) return config;
        Map<String, Object> map;
        try {
            map = objectMapper.readValue(config, Map.class);
        } catch (Exception e) {
            return config;
        }
        switch (np.getNodeType()) {
            case "DB_QUERY" -> {
                Object v = map.get("datasourceId");
                if (v instanceof String name) {
                    Long id = dsIdByName.get(name);
                    map.put("datasourceId", id);
                    if (id == null) result.getWarnings().add("流程「" + flowName + "」节点「" + np.getNodeName()
                            + "」引用的数据源「" + name + "」不存在，绑定已置空");
                }
            }
            case "REF_DB_SCRIPT" -> remapUidRef(map, "dbScriptId", dbScriptIdByUid, "数据库脚本", np, flowName, result);
            case "REF_API" -> remapUidRef(map, "apiConfigId", apiIdByUid, "接口配置", np, flowName, result);
            case "REF_PROGRAM_SCRIPT" -> remapPsRef(map, psScriptIdByUid, np, flowName, result);
            case "REDIS" -> remapUidRef(map, "redisConfigId", redisScriptIdByUid, "Redis脚本", np, flowName, result);
            case "REF_FLOW" -> remapUidRef(map, "flowId", flowIdByUid, "子流程", np, flowName, result);
            default -> {
            }
        }
        try {
            return objectMapper.writeValueAsString(map);
        } catch (Exception e) {
            return config;
        }
    }

    private void remapUidRef(Map<String, Object> map, String key, Map<String, Long> idByUid,
                             String typeDesc, NodePack np, String flowName, ImportResultVO result) {
        Object v = map.get(key);
        if (v == null) return;
        if (v instanceof Number) return; // 已是数字 id（手改包），保持原样
        Long id = idByUid.get(String.valueOf(v));
        map.put(key, id);
        if (id == null) {
            result.getWarnings().add("流程「" + flowName + "」节点「" + np.getNodeName() + "」引用的" + typeDesc
                    + "不在导入包中，引用已置空");
        }
    }

    /** 程序脚本引用重映射：config 中存 scriptId 业务字符串（非数字 id），导入后写回本地 scriptId */
    private void remapPsRef(Map<String, Object> map, Map<String, String> psScriptIdByUid,
                            NodePack np, String flowName, ImportResultVO result) {
        Object v = map.get("programScriptId");
        if (v == null || v instanceof Number) return;
        String key = String.valueOf(v);
        String scriptId = psScriptIdByUid.get(key);
        if (scriptId == null) {
            // 兼容旧版导出包：config 值未改写为 uid 仍是源环境 scriptId，本地已有同 scriptId 脚本则保留
            ProgramScript local = importExportMapper.selectProgramScriptByScriptId(key);
            if (local != null) scriptId = local.getScriptId();
        }
        map.put("programScriptId", scriptId);
        if (scriptId == null) {
            result.getWarnings().add("流程「" + flowName + "」节点「" + np.getNodeName() + "」引用的程序脚本"
                    + "不在导入包中，引用已置空");
        }
    }

    private void importVariables(FlowPack fp, Long flowId, Map<String, Long> paramIdByName,
                                 Map<String, Long> dsIdByName, Map<String, Long> nodeIdByCode) {
        List<FlowVariable> old = flowVariableMapper.selectByFlowId(flowId);
        if (!old.isEmpty()) {
            flowVariableMapper.deleteByIds(old.stream().map(FlowVariable::getId).toList());
        }
        for (VarPack v : fp.getVariables()) {
            FlowVariable fv = new FlowVariable();
            fv.setFlowId(flowId);
            fv.setVarName(v.getVarName());
            if (v.getParamName() != null) fv.setParamId(paramIdByName.get(v.getParamName()));
            if (v.getNodeCode() != null) {
                fv.setNodeCode(v.getNodeCode());
                fv.setNodeId(nodeIdByCode.get(v.getNodeCode()));
            }
            if (v.getDatasourceName() != null) fv.setDatasourceId(dsIdByName.get(v.getDatasourceName()));
            flowVariableMapper.insert(fv);
        }
    }

    // ==================== 缓存失效 ====================

    private void evictCaches(Long userId, Set<Long> executedFlowIds) {
        Long uid = userId != null ? userId : 0;
        redisCacheUtil.evictByPattern(RedisCacheUtil.buildKey("flow:user", uid, "*"));
        redisCacheUtil.evictByPattern(RedisCacheUtil.buildKey("db_script:user", uid, "*"));
        redisCacheUtil.evictByPattern(RedisCacheUtil.buildKey("program_script:user", uid, "*"));
        redisCacheUtil.evictByPattern(RedisCacheUtil.buildKey("api_config:user", uid, "*"));
        redisCacheUtil.evictByPattern(RedisCacheUtil.buildKey("redis_script:user", uid, "*"));
        redisCacheUtil.evictByPattern(RedisCacheUtil.buildKey("flow_variables:user", uid, "*"));
        redisCacheUtil.evictByPattern(RedisCacheUtil.buildKey("app_market:user", uid, "*"));
        for (Long flowId : executedFlowIds) {
            redisCacheUtil.evict(RedisCacheUtil.buildKey(SubFlowExecutor.FLOW_NODES_CACHE_PREFIX, flowId));
        }
        // 全局参数定义可能新增，JVM + Redis 一并失效（参数值缓存随之重建）
        globalParamService.invalidateCache();
    }

    // ==================== 工具方法 ====================

    private TransferPackage parsePackage(String packageJson) {
        AssertUtils.hasText(packageJson, ErrorCode.IMPORT_PACKAGE_INVALID, "内容为空");
        TransferPackage pkg;
        try {
            pkg = objectMapper.readValue(packageJson, TransferPackage.class);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.IMPORT_PACKAGE_INVALID, "JSON 解析失败");
        }
        AssertUtils.isTrue(TransferPackage.FORMAT.equals(pkg.getFormat()),
                ErrorCode.IMPORT_PACKAGE_INVALID, "不是有效的 arbor-package 文件");
        AssertUtils.isTrue(pkg.getVersion() != null && pkg.getVersion() == TransferPackage.VERSION,
                ErrorCode.IMPORT_PACKAGE_INVALID, "不支持的包版本 " + pkg.getVersion());
        return pkg;
    }

    /** 冲突策略：无冲突=CREATE；有冲突时单项覆盖优先，否则用默认策略 */
    private String resolveAction(Map<String, String> overrides, String key, boolean conflict, String defAction) {
        if (!conflict) return "CREATE";
        if (overrides != null && overrides.containsKey(key)) {
            return OVERWRITE.equalsIgnoreCase(overrides.get(key)) ? OVERWRITE : SKIP;
        }
        return defAction;
    }

    private String generateUniqueName(String baseName, Long userId) {
        if (baseName == null || baseName.isBlank()) baseName = "导入流程";
        String name = baseName;
        int n = 0;
        while (flowMapper.countByNameAndUser(name, userId) > 0) {
            n++;
            name = baseName + "(" + n + ")";
        }
        return name;
    }

    /** 同名流程数量（排除指定 id 自身） */
    private long countOtherFlowByName(String name, Long userId, Long excludeId) {
        long total = flowMapper.countByNameAndUser(name, userId);
        if (total == 0) return 0;
        Flow self = flowMapper.selectById(excludeId);
        return self != null && name.equals(self.getName()) ? total - 1 : total;
    }
}
