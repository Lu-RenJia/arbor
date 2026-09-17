package com.arbor.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 导入导出请求 DTO
 * <p>
 * 导出：flowIds / appIds 二选一；预览与执行：packageJson 必传
 * </p>
 *
 * @author gwl
 */
@Data
public class ImportExportDTO {

    /** 导出的流程ID列表 */
    private List<Long> flowIds;

    /** 导出的应用ID列表 */
    private List<Long> appIds;

    /** 导入包 JSON（预览/执行时传入） */
    private String packageJson;

    /** 冲突默认策略：SKIP-跳过（默认） / OVERWRITE-覆盖 */
    private String defaultConflictAction;

    /** 单项覆盖策略：uid → SKIP / OVERWRITE（优先于默认策略） */
    private java.util.Map<String, String> overrides;

    /**
     * 导入包结构（arbor-package v1）
     * <p>
     * 设计约定：
     * - 资源引用一律用 uid / 数据源名称表达，不含任何环境相关自增 id
     * - 节点 config 中的 datasourceId/dbScriptId/apiConfigId/redisConfigId/programScriptId/flowId
     *   在导出时已被改写为 数据源名称 / uid 字符串，导入时再映射回本地 id
     * - 全局参数只含定义（paramName/remark），参数值不导出
     * - 数据源只含名称提示，连接信息不导出，目标库必须预先存在同名数据源
     * </p>
     */
    @Data
    public static class TransferPackage {
        public static final String FORMAT = "arbor-package";
        public static final int VERSION = 1;

        private String format = FORMAT;
        private Integer version = VERSION;
        private String exportedAt;

        private List<FlowPack> flows = new ArrayList<>();
        private List<AppPack> applications = new ArrayList<>();
        private ResourcePack resources = new ResourcePack();
        private List<ParamPack> globalParams = new ArrayList<>();
        /** 数据源提示（仅名称/类型，无连接信息） */
        private List<DsHint> datasources = new ArrayList<>();
        private List<DsHint> redisDatasources = new ArrayList<>();
    }

    @Data
    public static class FlowPack {
        private String uid;
        private String name;
        private String description;
        private Integer status;
        private List<NodePack> nodes = new ArrayList<>();
        private List<VarPack> variables = new ArrayList<>();
        /** 内联资源（flow_id 归属本流程） */
        private List<DbScriptPack> dbScripts = new ArrayList<>();
        private List<ProgramScriptPack> programScripts = new ArrayList<>();
        private List<ApiConfigPack> apiConfigs = new ArrayList<>();
        private List<RedisScriptPack> redisScripts = new ArrayList<>();
    }

    @Data
    public static class NodePack {
        private String nodeCode;
        private String nodeName;
        private String nodeType;
        private Integer sortOrder;
        /** 已改写为 uid/数据源名称引用的 JSON 字符串 */
        private String config;
        private String exampleOutput;
        /** 分支父节点编码（替代环境相关的 branchOf id） */
        private String branchOfCode;
    }

    @Data
    public static class VarPack {
        private String varName;
        private String paramName;
        private String nodeCode;
        private String datasourceName;
    }

    @Data
    public static class DbScriptPack {
        private String uid;
        private String name;
        private String datasourceName;
        private String sqlContent;
        private Integer returnLimit;
        private Integer queryTimeout;
        private String remark;
    }

    @Data
    public static class ProgramScriptPack {
        private String uid;
        /** scriptId 业务字符串（节点 config 引用的是它而非数字 id），导入时用于兼容旧包映射 */
        private String scriptId;
        private String name;
        private String scriptContent;
        private Integer queryTimeout;
        private String remark;
    }

    @Data
    public static class ApiConfigPack {
        private String uid;
        private String name;
        private String method;
        private String url;
        private Integer timeout;
        private String headers;
        private String params;
        private String bodyType;
        private String bodyContent;
        private String remark;
    }

    @Data
    public static class RedisScriptPack {
        private String uid;
        private String name;
        private String datasourceName;
        /**
         * 业务前缀值。与数据源同口径按「名称」而非 id 表达（id 是环境相关自增）：
         * 导入时在目标数据源下按值查找，缺失则自动登记一个同值前缀。
         */
        private String prefixValue;
        /** 业务前缀名称（仅新建前缀时用作显示名） */
        private String prefixName;
        private String command;
        private String redisKey;
        private String redisValue;
        private Integer ttl;
        private String ttlUnit;
        private String remark;
    }

    @Data
    public static class ResourcePack {
        private List<DbScriptPack> dbScripts = new ArrayList<>();
        private List<ProgramScriptPack> programScripts = new ArrayList<>();
        private List<ApiConfigPack> apiConfigs = new ArrayList<>();
        private List<RedisScriptPack> redisScripts = new ArrayList<>();
    }

    @Data
    public static class ParamPack {
        private String paramName;
        private String remark;
    }

    @Data
    public static class AppPack {
        private String uid;
        private String name;
        private String flowUid;
        /** 环境代码（导入时按 code 匹配本地环境，匹配失败置为默认环境） */
        private String envCode;
        private String inputParams;
        private String remark;
    }

    @Data
    public static class DsHint {
        private String name;
        private String dbType;
    }
}
