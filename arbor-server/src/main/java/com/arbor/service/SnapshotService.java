package com.arbor.service;

import com.arbor.entity.Flow;
import com.arbor.entity.FlowNode;
import com.arbor.entity.FlowNodeSnapshot;
import com.arbor.entity.FlowSnapshot;
import com.arbor.mapper.SnapshotMapper;
import com.arbor.util.RedisCacheUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SnapshotService {

    private final SnapshotMapper mapper;
    private final RedisCacheUtil redisCacheUtil;

    static final String SNAPSHOT_CACHE = "flow_snapshot:flow:";

    /** 获取流程最新快照（带缓存） */
    public FlowSnapshot getLatestSnapshot(Long flowId) {
        String key = SNAPSHOT_CACHE + flowId;
        return redisCacheUtil.get(key, FlowSnapshot.class)
                .orElseGet(() -> {
                    FlowSnapshot s = mapper.selectLatestByFlowId(flowId);
                    if (s != null) redisCacheUtil.set(key, s, RedisCacheUtil.randomizedTtl());
                    return s;
                });
    }

    /** 获取快照节点列表 */
    public List<FlowNodeSnapshot> getSnapshotNodes(Long snapshotId) {
        return mapper.selectBySnapshotId(snapshotId);
    }

    /**
     * 创建流程快照（如果已有日志引用）
     * 先删旧快照 → 再新增，version = 上次版本 + 1
     */
    @Transactional
    public FlowSnapshot createIfReferenced(Flow flow, List<FlowNode> nodes) {
        if (mapper.countExecutionLogByFlowId(flow.getId()) == 0) {
            return null; // 无日志引用，不创建快照
        }
        FlowSnapshot last = mapper.selectLatestByFlowId(flow.getId());
        int version = (last != null ? last.getVersion() : 0) + 1;

        mapper.deleteByFlowId(flow.getId());

        FlowSnapshot snapshot = new FlowSnapshot();
        snapshot.setFlowId(flow.getId());
        snapshot.setVersion(version);
        snapshot.setName(flow.getName());
        snapshot.setDescription(flow.getDescription());
        snapshot.setStatus(flow.getStatus());
        snapshot.setNodeCount(flow.getNodeCount());
        mapper.insertSnapshot(snapshot);

        if (nodes != null && !nodes.isEmpty()) {
            List<FlowNodeSnapshot> nodeSnapshots = new ArrayList<>();
            for (FlowNode n : nodes) {
                FlowNodeSnapshot ns = new FlowNodeSnapshot();
                ns.setSnapshotId(snapshot.getId());
                ns.setNodeId(n.getId());
                ns.setFlowId(n.getFlowId());
                ns.setNodeCode(n.getNodeCode());
                ns.setNodeName(n.getNodeName());
                ns.setNodeType(n.getNodeType());
                ns.setSortOrder(n.getSortOrder());
                ns.setConfig(n.getConfig());
                ns.setBranchOf(n.getBranchOf());
                nodeSnapshots.add(ns);
            }
            if (!nodeSnapshots.isEmpty()) {
                mapper.insertNodeSnapshots(nodeSnapshots);
            }
        }

        redisCacheUtil.evict(SNAPSHOT_CACHE + flow.getId());
        log.info("流程快照: flowId={}, version={}", flow.getId(), version);
        return snapshot;
    }
}
