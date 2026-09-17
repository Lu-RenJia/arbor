package com.arbor.vo;

import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 引用追踪结果 VO（有向图结构，供前端 Vue Flow 渲染）
 *
 * @author gwl
 */
@Data
@Builder
public class ResourceTraceVO {

    /** 图节点列表 */
    @Builder.Default
    private List<TraceNode> nodes = new ArrayList<>();

    /** 图边列表 */
    @Builder.Default
    private List<TraceEdge> edges = new ArrayList<>();

    /** 根节点资源名称（用于抽屉标题） */
    private String rootName;

    /** 根节点资源类型 */
    private String rootType;

    // ====== 内嵌类 ======

    @Data
    @Builder
    public static class TraceNode {
        /** 唯一标识，格式: "type:id"，如 "datasource:3" */
        private String id;
        /** 显示名称 */
        private String label;
        /** 资源类型: global_param/datasource/db_script/program_script/api_config/redis_datasource/redis_script/flow/application/flow_node */
        private String type;
        /** 原始资源 ID（字符串，program_script 的 scriptId 是字符串） */
        private String resourceId;
        /** 引用方式说明，如 "host字段引用"、"变量池引用"、"REF_DB_SCRIPT节点" */
        private String relation;
        /** 层级深度（根节点=0） */
        private int level;
    }

    @Data
    @Builder
    public static class TraceEdge {
        /** 起点节点 id */
        private String source;
        /** 终点节点 id */
        private String target;
        /** 边标签（引用方式） */
        private String label;
    }
}
