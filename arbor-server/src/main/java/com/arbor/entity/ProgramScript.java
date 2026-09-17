package com.arbor.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 程序脚本配置实体
 *
 * @author gwl
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ProgramScript extends BaseEntity {

    /** 脚本ID（系统自动生成） */
    private String scriptId;

    /** 脚本名称 */
    private String name;

    /** Groovy脚本内容（支持${}全局参数） */
    private String scriptContent;

    /** 自定义超时秒数，NULL=系统默认 */
    private Integer queryTimeout;

    /** 备注 */
    private String remark;

    /** 是否为工具类: 0=普通脚本, 1=工具类 */
    private Integer isTool;

    /** 工具类类名（is_tool=1时有效，全局唯一） */
    private String toolClassName;

    /** 所属流程ID（NULL=全局资源，非空=内联资源） */
    private Long flowId;

    /** 全局唯一标识（导入导出匹配用，应用层创建时生成UUID） */
    private String uid;
}
