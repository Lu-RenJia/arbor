package com.arbor.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 导入预览结果 VO
 *
 * @author gwl
 */
@Data
public class ImportPreviewVO {

    /** 待导入项清单（流程/应用/共享资源/全局参数） */
    private List<PreviewItem> items = new ArrayList<>();

    /** 目标环境缺失的全局参数（被引用但不存在，参数值需导入后手工配置） */
    private List<String> missingParams = new ArrayList<>();

    /** 目标环境缺失的 MySQL 数据源名称（节点/脚本将无法绑定数据源） */
    private List<String> missingDatasources = new ArrayList<>();

    /** 目标环境缺失的 Redis 数据源名称 */
    private List<String> missingRedisDatasources = new ArrayList<>();

    /** 其他提示 */
    private List<String> warnings = new ArrayList<>();

    private int newCount;
    private int conflictCount;

    @Data
    public static class PreviewItem {
        /** 类型：FLOW/APP/DB_SCRIPT/PROGRAM_SCRIPT/API_CONFIG/REDIS_SCRIPT/GLOBAL_PARAM */
        private String type;
        /** 唯一标识（全局参数用 paramName） */
        private String uid;
        /** 包内名称 */
        private String name;
        /** 目标环境是否已存在同 uid（同 paramName）对象 */
        private boolean conflict;
        /** 目标环境已存在时的现名（供比对） */
        private String existingName;
        /** 内联资源归属的流程名（仅内联资源有值，随所属流程导入） */
        private String ownerFlowName;
    }
}
