package com.arbor.dto;

import lombok.Data;

import java.util.List;

/**
 * 流程保存DTO（含节点列表）
 *
 * @author gwl
 */
@Data
public class FlowSaveDTO {

    /** 流程名称（创建/更新时使用） */
    private String name;

    /** 流程描述 */
    private String description;

    /** 节点列表 */
    private List<FlowNodeDTO> nodes;

    @Data
    public static class FlowNodeDTO {
        /** 节点编码 */
        private String nodeCode;
        /** 节点名称 */
        private String nodeName;
        /** 节点类型（START/END/DB_QUERY/...） */
        private String nodeType;
        /** 排序号 */
        private Integer sortOrder;
        /** 节点配置（JSON字符串） */
        private String config;
        /** 分支父节点code（REDIS节点的分支子节点，null=主线） */
        private String branchOfCode;
    }
}
