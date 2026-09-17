package com.arbor.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 应用管理实体
 *
 * @author gwl
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class Application extends BaseEntity {

    /** 应用名称 */
    private String name;
    /** 关联的流程ID */
    private Long flowId;
    /** 所属用户ID */
    private Long userId;
    /** 执行环境ID，NULL=默认环境 */
    private Long envId;
    /** 默认入参（JSON字符串或纯文本） */
    private String inputParams;
    /** 备注 */
    private String remark;
    /** 全局唯一标识（导入导出匹配用，应用层创建时生成UUID） */
    private String uid;
}
