package com.arbor.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 数据库脚本配置实体
 *
 * @author gwl
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class DbScript extends BaseEntity {

    /** 脚本名称 */
    private String name;

    /** 关联的数据源ID */
    private Long datasourceId;

    /** SQL语句（支持${}全局参数） */
    private String sqlContent;

    /** 返回条数，默认10，范围1-200 */
    private Integer returnLimit;

    /** 自定义超时秒数，NULL=系统默认 */
    private Integer queryTimeout;

    /** 备注 */
    private String remark;

    /** 所属流程ID（NULL=全局资源，非空=内联资源） */
    private Long flowId;

    /** 全局唯一标识（导入导出匹配用，应用层创建时生成UUID） */
    private String uid;
}
