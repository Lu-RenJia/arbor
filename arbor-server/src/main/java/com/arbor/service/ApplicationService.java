package com.arbor.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.arbor.common.AssertUtils;
import com.arbor.common.BusinessException;
import com.arbor.common.EnvironmentContext;
import com.arbor.common.ErrorCode;
import com.arbor.common.TenantContext;
import com.arbor.common.PageResult;
import com.arbor.dto.ApplicationDTO;
import com.arbor.dto.FlowSaveDTO;
import com.arbor.engine.FlowExecutionResult;
import com.arbor.engine.NodeResult;
import com.arbor.entity.Application;
import com.arbor.entity.Environment;
import com.arbor.entity.ExecutionLog;
import com.arbor.entity.ExecutionNodeLog;
import com.arbor.entity.Flow;
import com.arbor.entity.FlowNode;
import com.arbor.entity.FlowNodeSnapshot;
import com.arbor.entity.FlowSnapshot;
import com.arbor.mapper.ApplicationMapper;
import com.arbor.mapper.ExecutionLogMapper;
import com.arbor.mapper.ExecutionNodeLogMapper;
import com.arbor.mapper.FlowMapper;
import com.arbor.mapper.FlowNodeMapper;
import com.arbor.vo.ApplicationVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 应用管理服务
 *
 * @author gwl
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApplicationService {

    private final ApplicationMapper mapper;
    private final FlowMapper flowMapper;
    private final FlowNodeMapper flowNodeMapper;
    private final com.arbor.mapper.FileResourceMapper fileResourceMapper;
    private final FlowDtoExecutionService flowDtoExecutionService;
    private final EnvironmentService environmentService;
    private final SnapshotService snapshotService;
    private final AppMarketService appMarketService;
    private final ExecutionLogMapper executionLogMapper;
    private final ExecutionNodeLogMapper executionNodeLogMapper;
    private final ExecutionScriptLogService executionScriptLogService;
    private final ObjectMapper objectMapper;

    public PageResult<ApplicationVO> page(int pageNum, int pageSize, String name, String flowName) {
        IPage<Application> page = new Page<>(pageNum, pageSize);
        IPage<Application> result = mapper.selectPage(page, name, flowName);
        var flowIds = result.getRecords().stream().map(Application::getFlowId).collect(Collectors.toSet());
        var flowMap = flowIds.isEmpty() ? Map.<Long, Flow>of()
                : flowMapper.selectByIds(flowIds).stream().collect(Collectors.toMap(Flow::getId, f -> f));
        Map<Long, Environment> envMap = environmentService.getEnvMap();

        // 批量解析 START 节点 config：文件上传开关 + 示例文件开关 + 参数表单 schema
        Map<Long, Boolean> fileUploadMap = new java.util.HashMap<>();
        Map<Long, Boolean> exampleEnabledMap = new java.util.HashMap<>();
        Map<Long, Object> paramSchemaMap = new java.util.HashMap<>();
        for (Long fid : flowIds) {
            var nodes = flowNodeMapper.selectByFlowId(fid);
            var startNode = nodes.stream().filter(n -> "START".equals(n.getNodeType())).findFirst().orElse(null);
            if (startNode != null && startNode.getConfig() != null) {
                try {
                    var cfg = objectMapper.readTree(startNode.getConfig());
                    fileUploadMap.put(fid, cfg.path("fileUploadEnabled").asBoolean(false));
                    exampleEnabledMap.put(fid, cfg.path("exampleEnabled").asBoolean(false));
                    if (cfg.has("paramSchema") && !cfg.path("paramSchema").isNull()) {
                        // 转成纯 Map/List：Spring Boot 4 HTTP 层用 Jackson3，无法正确序列化 Jackson2 的 JsonNode
                        paramSchemaMap.put(fid, objectMapper.convertValue(cfg.get("paramSchema"), Object.class));
                    }
                } catch (Exception ignored) {}
            }
        }
        // 批量加载示例文件（仅开启示例的流程）
        Map<Long, com.arbor.entity.FileResource> exampleFileMap = new java.util.HashMap<>();
        for (var e : exampleEnabledMap.entrySet()) {
            if (e.getValue()) {
                var fr = fileResourceMapper.selectByFlowId(e.getKey());
                if (fr != null) exampleFileMap.put(e.getKey(), fr);
            }
        }

        List<ApplicationVO> voList = result.getRecords().stream().map(a -> {
            Flow flow = flowMap.get(a.getFlowId());
            String envName = null;
            if (a.getEnvId() != null) {
                Environment env = envMap.get(a.getEnvId());
                if (env != null) envName = env.getName();
            }
            ApplicationVO vo = ApplicationVO.fromEntity(a, flow != null ? flow.getName() : "", envName);
            vo.setFileUploadEnabled(fileUploadMap.getOrDefault(a.getFlowId(), false));
            vo.setExampleEnabled(exampleEnabledMap.getOrDefault(a.getFlowId(), false));
            vo.setParamSchema(paramSchemaMap.get(a.getFlowId()));
            var exampleFile = exampleFileMap.get(a.getFlowId());
            if (exampleFile != null) {
                vo.setExampleFileId(exampleFile.getId());
                vo.setExampleFileName(exampleFile.getFileName());
            }
            return vo;
        }).collect(Collectors.toList());
        return PageResult.of(pageNum, pageSize, result.getTotal(), voList);
    }

    @Transactional
    public ApplicationVO create(ApplicationDTO dto) {
        Application entity = new Application();
        entity.setName(dto.getName());
        entity.setFlowId(dto.getFlowId());
        entity.setEnvId(dto.getEnvId());
        entity.setInputParams(dto.getInputParams());
        entity.setRemark(dto.getRemark());
        entity.setUid(java.util.UUID.randomUUID().toString());
        mapper.insert(entity);
        Flow flow = flowMapper.selectById(dto.getFlowId());
        log.info("新增应用: {} -> 流程: {}", dto.getName(), flow != null ? flow.getName() : "");
        return ApplicationVO.fromEntity(entity, flow != null ? flow.getName() : "");
    }

    @Transactional
    public ApplicationVO update(Long id, ApplicationDTO dto) {
        Application entity = mapper.selectById(id);
        AssertUtils.notNull(entity, ErrorCode.APPLICATION_NOT_FOUND);
        entity.setName(dto.getName());
        entity.setFlowId(dto.getFlowId());
        entity.setEnvId(dto.getEnvId());
        entity.setInputParams(dto.getInputParams());
        entity.setRemark(dto.getRemark());
        mapper.update(entity);
        Flow flow = flowMapper.selectById(dto.getFlowId());
        return ApplicationVO.fromEntity(entity, flow != null ? flow.getName() : "");
    }

    /** 共享应用到广场（指定多个环境） */
    public void shareToMarket(Long appId, Long marketId, List<Long> envIds) {
        appMarketService.shareApp(appId, marketId, envIds);
    }

    /** 取消共享 */
    public void unshareFromMarket(Long appId, Long marketId) {
        appMarketService.unshareApp(appId, marketId);
    }

    /** 查询某应用已共享到哪些广场（我的应用页展示与移除用）；仅属主或管理员可查 */
    public List<com.arbor.vo.AppShareVO> getSharedMarkets(Long appId) {
        Application app = mapper.selectById(appId);
        AssertUtils.notNull(app, ErrorCode.APPLICATION_NOT_FOUND);
        Long userId = TenantContext.getUserId();
        if (!TenantContext.isAdmin() && !userId.equals(app.getUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权查看此应用的共享情况");
        }
        return appMarketService.getSharesByApp(appId);
    }

    @Transactional
    public void delete(Long id) {
        AssertUtils.notNull(mapper.selectById(id), ErrorCode.APPLICATION_NOT_FOUND);
        mapper.deleteById(id);
    }

    /** 执行应用：快照 → 日志 → 执行 → 节点日志 */
    public Object execute(Long id, Long marketId, Long envId, MultipartFile file) {
        Application app = mapper.selectById(id);
        AssertUtils.notNull(app, ErrorCode.APPLICATION_NOT_FOUND);

        // 权限校验：应用所有者 或 已分配到共享广场的用户
        Long userId = TenantContext.getUserId();
        if (!TenantContext.isAdmin()
                && !userId.equals(app.getUserId())
                && !appMarketService.canExecuteApp(id, userId, marketId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权执行此应用");
        }

        // 执行期间临时跳过租户过滤（广场共享的 app 及其引用的所有资源可能属于其他用户）
        TenantContext.skipTenant(true);
        try {
            Flow flow = flowMapper.selectById(app.getFlowId());
            AssertUtils.notNull(flow, ErrorCode.APPLICATION_FLOW_NOT_FOUND);

            // 广场执行：优先用共享记录中的 envId（所有者的环境）；否则用 app 自身的 envId
            Long effectiveEnvId = envId != null ? envId
                    : (app.getEnvId() != null ? app.getEnvId() : environmentService.getDefaultEnvId());
            Environment env = environmentService.getByIdCached(effectiveEnvId);
            AssertUtils.notNull(env, ErrorCode.ENV_NOT_FOUND);

            // 使用快照或实时数据
            FlowSnapshot snapshot = snapshotService.getLatestSnapshot(flow.getId());
            List<FlowNode> nodes;
            if (snapshot != null) {
                List<FlowNodeSnapshot> snapshotNodes = snapshotService.getSnapshotNodes(snapshot.getId());
                nodes = snapshotNodes.stream().map(ns -> {
                    FlowNode fn = new FlowNode();
                    fn.setId(ns.getNodeId());
                    fn.setFlowId(ns.getFlowId());
                    fn.setNodeCode(ns.getNodeCode());
                    fn.setNodeName(ns.getNodeName());
                    fn.setNodeType(ns.getNodeType());
                    fn.setSortOrder(ns.getSortOrder());
                    fn.setConfig(ns.getConfig());
                    fn.setBranchOf(ns.getBranchOf());
                    return fn;
                }).collect(Collectors.toList());
            } else {
                nodes = flowNodeMapper.selectByFlowId(app.getFlowId());
            }

            EnvironmentContext.set(effectiveEnvId, env.getCode());
            return doExecute(app, flow, nodes, snapshot, file);
        } finally {
            TenantContext.skipTenant(false);
            EnvironmentContext.clear();
        }
    }

    private Object doExecute(Application app, Flow flow, List<FlowNode> nodes, FlowSnapshot snapshot, MultipartFile file) {
        List<FlowSaveDTO.FlowNodeDTO> nodeDTOs = new ArrayList<>();
        boolean hasStart = false;

        // 上传文件：编码 base64，注入 START 节点（应用执行传入的文件）
        String fileBase64 = null;
        String fileName = null;
        if (file != null && !file.isEmpty()) {
            try {
                fileName = file.getOriginalFilename();
                fileBase64 = "data:" + (file.getContentType() != null ? file.getContentType() : "application/octet-stream")
                        + ";base64," + java.util.Base64.getEncoder().encodeToString(file.getBytes());
                log.info("应用执行上传文件: name={}, size={}", fileName, file.getSize());
            } catch (Exception e) {
                log.error("读取上传文件失败", e);
                throw new BusinessException(com.arbor.common.ErrorCode.SYSTEM_ERROR, "读取上传文件失败: " + e.getMessage());
            }
        }

        for (FlowNode node : nodes) {
            FlowSaveDTO.FlowNodeDTO dto = new FlowSaveDTO.FlowNodeDTO();
            dto.setNodeCode(node.getNodeCode());
            dto.setNodeName(node.getNodeName());
            dto.setNodeType(node.getNodeType());
            dto.setSortOrder(node.getSortOrder());
            if ("START".equals(node.getNodeType())) {
                hasStart = true;
                // 以节点原有配置为基础，合并应用级别的入参和文件
                Map<String, Object> startCfg;
                try {
                    startCfg = node.getConfig() != null && !node.getConfig().isBlank()
                            ? objectMapper.readValue(node.getConfig(), Map.class)
                            : new java.util.LinkedHashMap<>();
                } catch (Exception e) {
                    startCfg = new java.util.LinkedHashMap<>();
                }
                // 应用 inputParams（覆盖节点原有 _input）
                // 仅当输入以 { 或 [ 开头时才尝试按 JSON 解析，避免普通字符串走一次失败的解析；
                // 字符串直接原样放入，序列化时 Jackson 会自动转义，无需手动转义（手动转义会导致双重转义）
                String params = app.getInputParams();
                if (params != null && !params.isBlank()) {
                    String trimmed = params.trim();
                    if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
                        try {
                            Object parsed = objectMapper.readTree(trimmed);
                            startCfg.put(com.arbor.engine.executor.StartNodeExecutor.FLOW_PARAM_KEY,
                                    parsed != null ? objectMapper.convertValue(parsed, Object.class) : params);
                        } catch (Exception e) {
                            // 形似 JSON 但解析失败：原样当字符串传递
                            startCfg.put(com.arbor.engine.executor.StartNodeExecutor.FLOW_PARAM_KEY, params);
                        }
                    } else {
                        startCfg.put(com.arbor.engine.executor.StartNodeExecutor.FLOW_PARAM_KEY, params);
                    }
                }
                // 上传文件
                if (fileBase64 != null) {
                    startCfg.put("_testFileName", fileName);
                    startCfg.put("_testFileData", fileBase64);
                }
                try {
                    dto.setConfig(objectMapper.writeValueAsString(startCfg));
                } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                    throw new BusinessException(com.arbor.common.ErrorCode.SYSTEM_ERROR, "START 节点配置序列化失败");
                }
            } else {
                dto.setConfig(node.getConfig());
            }
            nodeDTOs.add(dto);
        }

        if (!hasStart) {
            throw new BusinessException(ErrorCode.FLOW_NO_START_NODE);
        }

        log.info("执行应用: id={}, name={}, flowId={}, envId={}, 入参={}",
                app.getId(), app.getName(), app.getFlowId(), EnvironmentContext.getEnvId(), app.getInputParams());
        long start = System.currentTimeMillis();
        FlowExecutionResult result = flowDtoExecutionService.executeWithNodes(app.getFlowId(), nodeDTOs, flow);
        long duration = System.currentTimeMillis() - start;

        // 记录执行日志
        try {
            ExecutionLog execLog = new ExecutionLog();
            execLog.setFlowId(flow.getId());
            execLog.setFlowName(flow.getName());
            execLog.setUserId(com.arbor.common.TenantContext.getUserId());
            execLog.setAppId(app.getId());
            execLog.setFlowSnapshotId(snapshot != null ? snapshot.getId() : null);
            // input_params 是 JSON 列，非 JSON 字符串不写入
            execLog.setInputParams(toJsonOrNull(app.getInputParams()));
            execLog.setExecuteType(com.arbor.engine.FlowEngine.EXECUTE_MANUAL);
            execLog.setSource(com.arbor.engine.FlowEngine.SOURCE_APP);
            execLog.setOwnerId(app.getUserId());
            execLog.setExecutorId(com.arbor.common.TenantContext.getUserId());
            execLog.setStatus(result.getStatus() != null ? result.getStatus()
                    : (result.isSuccess() ? "SUCCESS" : "FAIL"));
            execLog.setStartTime(result.getStartTime());
            execLog.setEndTime(result.getEndTime());
            execLog.setDuration(result.getDuration());
            execLog.setErrorMessage(result.getErrorMessage());
            execLog.setStopType(result.getStopType());
            executionLogMapper.insert(execLog);

            // 记录节点日志：单个节点失败只跳过该节点，不再中断后续节点
            // （此前整个循环共用外层 try，一条序列化失败会丢掉后面所有节点的日志）
            if (result.getNodeDetailList() != null) {
                int sortOrder = 0;
                for (com.arbor.engine.NodeResult nr : result.getNodeDetailList()) {
                    try {
                        com.arbor.entity.ExecutionNodeLog nodeLog = new com.arbor.entity.ExecutionNodeLog();
                        nodeLog.setExecutionLogId(execLog.getId());
                        nodeLog.setNodeCode(nr.getNodeCode());
                        nodeLog.setNodeName(nr.getNodeName());
                        nodeLog.setNodeType(nr.getNodeType());
                        nodeLog.setSortOrder(++sortOrder);
                        nodeLog.setStatus(nr.getStatus() != null ? nr.getStatus()
                                : (nr.isSuccess() ? "SUCCESS" : "FAIL"));
                        nodeLog.setDuration(nr.getDuration());
                        nodeLog.setErrorMessage(nr.getErrorMessage());
                        // 只保留子流程内部节点状态（历史详情要展示）；节点返回数据不再落库 ——
                        // 大返回值会撞 MySQL JSON 列的 max_allowed_packet，且详情已不再渲染它，
                        // 需要观察的数据由脚本自己 log 出来
                        if (nr.getSubFlowNodeDetails() != null) {
                            try {
                                java.util.Map<String, Object> wrapped = new java.util.LinkedHashMap<>();
                                wrapped.put("subFlowNodes", nr.getSubFlowNodeDetails());
                                nodeLog.setOutputResult(objectMapper.writeValueAsString(wrapped));
                            } catch (Exception ignored) {
                                nodeLog.setOutputResult(null);
                            }
                        } else {
                            nodeLog.setOutputResult(null);
                        }
                        executionNodeLogMapper.insert(nodeLog);
                    } catch (Exception ne) {
                        log.error("记录节点日志失败: executionLogId={}, nodeCode={}",
                                execLog.getId(), nr.getNodeCode(), ne);
                    }
                }
            }

            // 记录脚本日志（独立一步，save 内部已兜异常，与节点日志互不影响）
            executionScriptLogService.save(execLog.getId(), result.getScriptLogs());
        } catch (Exception e) {
            log.error("记录执行日志失败", e);
        }

        AssertUtils.isTrue(result.isSuccess(), ErrorCode.FLOW_EXEC_FAILED, result.getErrorMessage());

        Map<String, Object> execResult = new java.util.LinkedHashMap<>();

        // 流程停止条件触发：返回停止提示信息
        if ("STOPPED".equals(result.getStatus())) {
            execResult.put("success", true);
            execResult.put("stopped", true);
            execResult.put("stopType", result.getStopType());
            execResult.put("data", result.getStopMessage());
            execResult.put("duration", duration);
            execResult.put("nodeCount", result.getNodeDetailList() != null ? result.getNodeDetailList().size() : 0);
            return execResult;
        }

        List<com.arbor.engine.NodeResult> nodeResults = result.getNodeDetailList();
        AssertUtils.isTrue(nodeResults != null && !nodeResults.isEmpty(), ErrorCode.FLOW_NO_EXEC_NODES);
        com.arbor.engine.NodeResult last = nodeResults.get(nodeResults.size() - 1);
        Object data = last.isSuccess() ? last.getData() : ("节点执行失败: " + last.getErrorMessage());

        execResult.put("success", last.isSuccess());
        execResult.put("data", data);
        execResult.put("duration", duration);
        execResult.put("nodeCount", nodeResults.size());
        // FILE 输出模式：传递文件下载信息给前端（fileName 可能为 null，不能用 Map.of）
        if (result.getOutputFile() != null) {
            var of = result.getOutputFile();
            Map<String, Object> ofMap = new java.util.LinkedHashMap<>();
            ofMap.put("fileName", of.getFileName());
            ofMap.put("fileId", of.getFileId());
            ofMap.put("size", of.getSize());
            ofMap.put("sizeText", of.getSizeText());
            execResult.put("outputFile", ofMap);
        }
        return execResult;
    }

    /** 安全转为 JSON 列可接受的值：合法 JSON 对象/数组原样返回，否则返回 null */
    private static String toJsonOrNull(String input) {
        if (input == null) return null;
        String trimmed = input.trim();
        if ((trimmed.startsWith("{") || trimmed.startsWith("[")) && trimmed.length() > 2) {
            return trimmed;
        }
        return null;
    }
}
