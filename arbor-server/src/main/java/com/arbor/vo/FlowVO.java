package com.arbor.vo;

import com.arbor.entity.Flow;
import com.arbor.entity.FlowNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 流程 VO
 *
 * @author gwl
 */
@Data
@Builder
public class FlowVO {

    private Long id;
    /** 流程名称 */
    private String name;
    /** 流程描述 */
    private String description;
    /** 状态：0-关闭 1-启用 */
    private Integer status;
    /** 节点数量 */
    private Integer nodeCount;
    /** 创建时间 */
    private LocalDateTime createTime;
    /** 更新时间 */
    private LocalDateTime updateTime;
    /** 节点列表（详情接口才返回） */
    private List<FlowNodeVO> nodes;
    /** 定时任务 Cron 表达式（列表展示用） */
    private String cronExpr;
    /** 定时任务是否启用 */
    private Integer scheduleEnabled;

    public static FlowVO fromEntity(Flow entity) {
        return FlowVO.builder()
                .id(entity.getId())
                .name(entity.getName())
                .description(entity.getDescription())
                .status(entity.getStatus())
                .nodeCount(entity.getNodeCount())
                .createTime(entity.getCreateTime())
                .updateTime(entity.getUpdateTime())
                .build();
    }

    public static FlowVO fromEntityWithNodes(Flow entity, List<FlowNode> nodes) {
        FlowVO vo = fromEntity(entity);
        if (nodes != null) {
            vo.setNodes(nodes.stream().map(FlowNodeVO::fromEntity).collect(Collectors.toList()));
        }
        return vo;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FlowNodeVO {
        private Long id;
        /** 流程ID */
        private Long flowId;
        /** 节点编码 */
        private String nodeCode;
        /** 节点名称 */
        private String nodeName;
        /** 节点类型 */
        private String nodeType;
        /** 排序号 */
        private Integer sortOrder;
        /** 节点配置（JSON字符串） */
        private String config;
        /** 示例输出 */
        private String exampleOutput;
        /** 分支归属节点ID */
        private Long branchOf;
        /** 创建时间 */
        private LocalDateTime createTime;
        /** 更新时间 */
        private LocalDateTime updateTime;

        public static FlowNodeVO fromEntity(FlowNode entity) {
            return FlowNodeVO.builder()
                    .id(entity.getId())
                    .flowId(entity.getFlowId())
                    .nodeCode(entity.getNodeCode())
                    .nodeName(entity.getNodeName())
                    .nodeType(entity.getNodeType())
                    .sortOrder(entity.getSortOrder())
                    .config(entity.getConfig())
                    .exampleOutput(entity.getExampleOutput())
                    .branchOf(entity.getBranchOf())
                    .createTime(entity.getCreateTime())
                    .updateTime(entity.getUpdateTime())
                    .build();
        }
    }
}
