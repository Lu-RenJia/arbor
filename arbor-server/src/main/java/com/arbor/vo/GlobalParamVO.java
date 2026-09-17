package com.arbor.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 全局参数 VO（列表展示 + 环境值）
 *
 * @author gwl
 */
@Data
@Builder
public class GlobalParamVO {

    private Long id;
    /** 参数名 */
    private String paramName;
    /** 备注 */
    private String remark;
    /** 参数引用ID：${paramName} */
    private String paramId;
    /** 各环境下的参数值 */
    private List<EnvParamValue> envValues;
    /** 创建时间 */
    private LocalDateTime createTime;
    /** 修改时间 */
    private LocalDateTime updateTime;

    // ====== 内嵌类：环境参数值 ======

    @Data
    @Builder
    public static class EnvParamValue {
        /** 参数值ID */
        private Long paramValueId;
        /** 环境ID */
        private Long envId;
        /** 环境名称 */
        private String envName;
        /** 环境代码 */
        private String envCode;
        /** 参数值 */
        private String paramValue;
        /** "正常" 或 "已停用" */
        private String status;
        /** 停用时间（环境被删除的时间） */
        private String disabledTime;
    }

    public static GlobalParamVO fromEntity(com.arbor.entity.GlobalParam entity) {
        return GlobalParamVO.builder()
                .id(entity.getId())
                .paramName(entity.getParamName())
                .remark(entity.getRemark())
                .paramId("${" + entity.getParamName() + "}")
                .createTime(entity.getCreateTime())
                .updateTime(entity.getUpdateTime())
                .build();
    }
}
