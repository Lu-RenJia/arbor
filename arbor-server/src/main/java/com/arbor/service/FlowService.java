package com.arbor.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.arbor.common.AssertUtils;
import com.arbor.common.ErrorCode;
import com.arbor.common.PageResult;
import com.arbor.common.TenantContext;
import com.arbor.dto.FlowSaveDTO;
import com.arbor.engine.FlowEngine;
import com.arbor.engine.FlowExecutionResult;
import com.arbor.engine.SubFlowExecutor;
import com.arbor.entity.Flow;
import com.arbor.entity.FlowNode;
import com.arbor.entity.FlowSchedule;
import com.arbor.mapper.FlowMapper;
import com.arbor.mapper.FlowNodeMapper;
import com.arbor.mapper.FlowScheduleMapper;
import com.arbor.schedule.ScheduleManager;
import com.arbor.util.RedisCacheUtil;
import com.arbor.vo.FlowVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 流程管理服务（CRUD + 节点配置管理）
 * <p>
 * DTO 路径执行逻辑（测试流程/应用执行）已拆至 {@link FlowDtoExecutionService}；
 * DB 路径执行由 {@link FlowEngine} 承担。
 * </p>
 *
 * @author gwl
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlowService {

    private final FlowMapper flowMapper;
    private final FlowNodeMapper flowNodeMapper;
    private final FlowScheduleMapper flowScheduleMapper;
    private final ScheduleManager scheduleManager;
    private final FlowEngine flowEngine;
    private final RedisCacheUtil redisCacheUtil;
    private final SnapshotService snapshotService;
    private final com.arbor.mapper.DbScriptMapper dbScriptMapper;
    private final com.arbor.mapper.ProgramScriptMapper programScriptMapper;
    private final com.arbor.mapper.ApiConfigMapper apiConfigMapper;
    private final com.arbor.mapper.RedisScriptMapper redisScriptMapper;

    /** 流程缓存 key 前缀（按用户隔离） */
    static final String FLOW_CACHE_PREFIX = "flow:user";

    private String flowCacheKey(Long id) {
        Long userId = TenantContext.getUserId();
        return RedisCacheUtil.buildKey(FLOW_CACHE_PREFIX, (userId != null ? userId : 0), "id", id);
    }

    /** 合法的排序字段白名单 */
    private static final java.util.Set<String> ALLOWED_SORT_FIELDS = java.util.Set.of(
            "create_time", "update_time", "node_count", "id");

    /** 合法的排序方向 */
    private static final java.util.Set<String> ALLOWED_SORT_ORDERS = java.util.Set.of("ASC", "DESC");

    /** 分页查询（支持模糊搜索、节点数过滤、排序） */
    public PageResult<FlowVO> page(int pageNum, int pageSize, String name,
                                    Integer nodeCount, String sortField, String sortOrder) {
        // 防 SQL 注入：白名单校验排序字段和方向（默认按修改时间倒序）
        if (sortField == null || !ALLOWED_SORT_FIELDS.contains(sortField)) {
            sortField = "update_time";
        }
        if (sortOrder == null || !ALLOWED_SORT_ORDERS.contains(sortOrder.toUpperCase())) {
            sortOrder = "DESC";
        } else {
            sortOrder = sortOrder.toUpperCase();
        }

        IPage<Flow> page = new Page<>(pageNum, pageSize);
        IPage<Flow> result = flowMapper.selectPage(page, name, nodeCount, sortField, sortOrder);
        List<FlowVO> voList = result.getRecords().stream().map(FlowVO::fromEntity).collect(Collectors.toList());
        // 批量填充定时任务信息
        if (!voList.isEmpty()) {
            java.util.Set<Long> flowIds = voList.stream().map(FlowVO::getId).collect(Collectors.toSet());
            Map<Long, FlowSchedule> scheduleMap = flowScheduleMapper.selectByFlowIds(flowIds)
                    .stream().collect(Collectors.toMap(FlowSchedule::getFlowId, s -> s));
            voList.forEach(vo -> {
                FlowSchedule s = scheduleMap.get(vo.getId());
                if (s != null) {
                    vo.setCronExpr(s.getCronExpr());
                    vo.setScheduleEnabled(s.getEnabled());
                }
            });
        }
        return PageResult.of(pageNum, pageSize, result.getTotal(), voList);
    }

    /** 分页查询（兼容旧调用） */
    public PageResult<FlowVO> page(int pageNum, int pageSize, String name) {
        return page(pageNum, pageSize, name, null, "update_time", "DESC");
    }

    /** 获取流程详情（含节点列表） */
    public FlowVO getDetail(Long id) {
        Flow flow = getFlowCached(id);
        AssertUtils.notNull(flow, ErrorCode.FLOW_NOT_FOUND);
        List<FlowNode> nodes = flowNodeMapper.selectByFlowId(id);
        return FlowVO.fromEntityWithNodes(flow, nodes);
    }

    /** 新增流程（含节点），同用户下名称不重复 */
    @Transactional
    public FlowVO create(FlowSaveDTO dto) {
        // 名称唯一性：同用户不重名
        String name = generateUniqueName(dto.getName(), TenantContext.getUserId());

        Flow flow = new Flow();
        flow.setName(name);
        flow.setDescription(dto.getDescription());
        flow.setStatus(0); // 默认关闭
        flow.setNodeCount(dto.getNodes() != null ? dto.getNodes().size() : 0);
        flow.setUid(java.util.UUID.randomUUID().toString());
        flowMapper.insert(flow);
        log.info("新增流程: id={}, name={}", flow.getId(), name);

        // 保存节点
        if (dto.getNodes() != null && !dto.getNodes().isEmpty()) {
            saveNodesInternal(flow.getId(), dto.getNodes());
        }

        return FlowVO.fromEntity(flow);
    }

    /** 保存节点内部实现（供 create 和 saveNodes 共用） */
    private void saveNodesInternal(Long flowId, List<FlowSaveDTO.FlowNodeDTO> nodeDTOs) {
        flowNodeMapper.deleteByFlowId(flowId);
        if (nodeDTOs == null || nodeDTOs.isEmpty()) return;

        List<FlowSaveDTO.FlowNodeDTO> mainDTOs = nodeDTOs.stream()
                .filter(n -> n.getBranchOfCode() == null || n.getBranchOfCode().isBlank())
                .collect(Collectors.toList());
        List<FlowSaveDTO.FlowNodeDTO> branchDTOs = nodeDTOs.stream()
                .filter(n -> n.getBranchOfCode() != null && !n.getBranchOfCode().isBlank())
                .collect(Collectors.toList());

        // 插入主线节点
        for (FlowSaveDTO.FlowNodeDTO dto : mainDTOs) {
            FlowNode node = new FlowNode();
            node.setFlowId(flowId);
            node.setNodeCode(dto.getNodeCode());
            node.setNodeName(dto.getNodeName());
            node.setNodeType(dto.getNodeType());
            node.setSortOrder(dto.getSortOrder());
            node.setConfig(dto.getConfig());
            flowNodeMapper.insert(node);
        }

        // 读取已插入的主线节点，建立 code → DB id 映射
        Map<String, Long> codeToId = new java.util.LinkedHashMap<>();
        for (FlowNode n : flowNodeMapper.selectByFlowId(flowId)) {
            codeToId.put(n.getNodeCode(), n.getId());
        }

        // 插入分支节点（解析 branchOfCode → DB id）
        for (FlowSaveDTO.FlowNodeDTO dto : branchDTOs) {
            Long parentId = codeToId.get(dto.getBranchOfCode());
            FlowNode node = new FlowNode();
            node.setFlowId(flowId);
            node.setNodeCode(dto.getNodeCode());
            node.setNodeName(dto.getNodeName());
            node.setNodeType(dto.getNodeType());
            node.setSortOrder(dto.getSortOrder());
            node.setConfig(dto.getConfig());
            node.setBranchOf(parentId);
            flowNodeMapper.insert(node);
        }

        flowMapper.updateNodeCount(flowId, nodeDTOs.size());
    }

    /** 生成同用户唯一名称：新流程 / 新流程(1) / 新流程(2) ... */
    private String generateUniqueName(String baseName, Long userId) {
        if (baseName == null || baseName.isBlank()) baseName = "新流程";
        String name = baseName;
        int n = 0;
        while (flowMapper.countByNameAndUser(name, userId) > 0) {
            n++;
            name = baseName + "(" + n + ")";
        }
        return name;
    }

    /** 修改流程基本信息 */
    @Transactional
    public FlowVO update(Long id, FlowSaveDTO dto) {
        Flow flow = getFlowCached(id);
        AssertUtils.notNull(flow, ErrorCode.FLOW_NOT_FOUND);
        flow.setName(dto.getName());
        flow.setDescription(dto.getDescription());
        flowMapper.update(flow);
        redisCacheUtil.evict(flowCacheKey(id));
        log.info("修改流程: id={}, name={}", id, dto.getName());
        return FlowVO.fromEntity(flow);
    }

    /** 删除流程（级联删除节点 + 内联资源） */
    @Transactional
    public void delete(Long id) {
        Flow flow = getFlowCached(id);
        AssertUtils.notNull(flow, ErrorCode.FLOW_NOT_FOUND);
        flowNodeMapper.deleteByFlowId(id);
        flowScheduleMapper.deleteByFlowId(id);
        scheduleManager.unregister(id);
        // 级联删除内联资源（flow_id 绑定的脚本/接口）
        dbScriptMapper.deleteByFlowId(id);
        programScriptMapper.deleteByFlowId(id);
        apiConfigMapper.deleteByFlowId(id);
        redisScriptMapper.deleteByFlowId(id);
        flowMapper.deleteById(id);
        redisCacheUtil.evict(flowCacheKey(id));
        redisCacheUtil.evict(RedisCacheUtil.buildKey(SubFlowExecutor.FLOW_NODES_CACHE_PREFIX, id));
        log.info("删除流程: id={}, name={}", id, flow.getName());
    }

    /** 启用/关闭流程 */
    @Transactional
    public void updateStatus(Long id, Integer status) {
        Flow flow = getFlowCached(id);
        AssertUtils.notNull(flow, ErrorCode.FLOW_NOT_FOUND);
        flowMapper.updateStatus(id, status);
        redisCacheUtil.evict(flowCacheKey(id));
        log.info("流程状态变更: id={}, status={}", id, status == 1 ? "启用" : "关闭");
    }

    /** 保存流程节点配置（先删后插） */
    @Transactional
    public void saveNodes(Long flowId, List<FlowSaveDTO.FlowNodeDTO> nodeDTOs) {
        Flow flow = getFlowCached(flowId);
        AssertUtils.notNull(flow, ErrorCode.FLOW_NOT_FOUND);

        saveNodesInternal(flowId, nodeDTOs);

        // 缓存清理
        redisCacheUtil.evict(flowCacheKey(flowId));
        redisCacheUtil.evict(RedisCacheUtil.buildKey(SubFlowExecutor.FLOW_NODES_CACHE_PREFIX, flowId));

        // 创建流程快照（如有日志引用）
        List<FlowNode> savedNodes = flowNodeMapper.selectByFlowId(flowId);
        snapshotService.createIfReferenced(flow, savedNodes);

        log.info("保存流程节点: flowId={}, nodeCount={}", flowId, nodeDTOs != null ? nodeDTOs.size() : 0);
    }

    /** DTO 转节点实体 */
    private FlowNode buildFlowNode(Long flowId, FlowSaveDTO.FlowNodeDTO dto, Long branchOf) {
        FlowNode node = new FlowNode();
        node.setFlowId(flowId);
        node.setNodeCode(dto.getNodeCode());
        node.setNodeName(dto.getNodeName());
        node.setNodeType(dto.getNodeType());
        node.setSortOrder(dto.getSortOrder());
        node.setConfig(dto.getConfig());
        node.setBranchOf(branchOf);
        return node;
    }

    /** 获取流程节点列表 */
    public List<FlowVO.FlowNodeVO> getNodes(Long flowId) {
        List<FlowNode> nodes = flowNodeMapper.selectByFlowId(flowId);
        return nodes.stream().map(FlowVO.FlowNodeVO::fromEntity).collect(Collectors.toList());
    }

    /** 保存节点示例输出 */
    @Transactional
    public void saveExamples(Long flowId, List<FlowVO.FlowNodeVO> nodeResults) {
        List<FlowNode> existingNodes = flowNodeMapper.selectByFlowId(flowId);
        for (FlowVO.FlowNodeVO vo : nodeResults) {
            for (FlowNode node : existingNodes) {
                if (node.getNodeCode().equals(vo.getNodeCode())) {
                    node.setExampleOutput(vo.getExampleOutput());
                    flowNodeMapper.update(node);
                    break;
                }
            }
        }
        log.info("保存流程节点示例输出: flowId={}, nodeCount={}", flowId, nodeResults.size());
    }

    /** 保存 + 执行，确保执行的是当前编辑状态 */
    @Transactional
    public FlowExecutionResult saveAndExecute(Long flowId, List<FlowSaveDTO.FlowNodeDTO> nodeDTOs) {
        saveNodes(flowId, nodeDTOs);
        return manualExecute(flowId);
    }

    /** 手动执行流程（使用已保存的节点） */
    public FlowExecutionResult manualExecute(Long flowId) {
        return flowEngine.execute(flowId, FlowEngine.EXECUTE_MANUAL, FlowEngine.SOURCE_FLOW_MANUAL);
    }

    /** 带 Redis 缓存的按 ID 查询流程 */
    private Flow getFlowCached(Long id) {
        String key = flowCacheKey(id);
        return redisCacheUtil.get(key, Flow.class)
                .orElseGet(() -> {
                    Flow flow = flowMapper.selectById(id);
                    if (flow != null) {
                        redisCacheUtil.set(key, flow, com.arbor.util.RedisCacheUtil.randomizedTtl());
                    }
                    return flow;
                });
    }
}
