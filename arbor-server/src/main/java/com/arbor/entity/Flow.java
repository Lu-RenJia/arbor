package com.arbor.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 流程定义实体
 *
 * @author gwl
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class Flow extends BaseEntity {

    /** 流程名称 */
    private String name;

    /** 流程描述 */
    private String description;

    /** 所属用户ID */
    private Long userId;

    /** 状态：0-关闭 1-启用 */
    private Integer status;

    /** 节点数量 */
    private Integer nodeCount;

    /** 全局唯一标识（导入导出匹配用，应用层创建时生成UUID） */
    private String uid;
}
