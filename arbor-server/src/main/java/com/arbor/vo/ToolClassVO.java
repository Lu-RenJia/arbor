package com.arbor.vo;

import com.arbor.entity.ProgramScript;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 工具类 VO
 *
 * @author gwl
 */
@Data
@Builder
public class ToolClassVO {

    private Long id;
    /** 脚本唯一标识 */
    private String scriptId;
    /** 工具类名称（中文描述） */
    private String name;
    /** 解析出的类名 */
    private String toolClassName;
    /** Groovy 类源码 */
    private String scriptContent;
    /** 备注 */
    private String remark;
    /** 创建时间 */
    private LocalDateTime createTime;
    /** 更新时间 */
    private LocalDateTime updateTime;

    /**
     * 从实体转换为 VO
     */
    public static ToolClassVO fromEntity(ProgramScript entity) {
        return ToolClassVO.builder()
                .id(entity.getId())
                .scriptId(entity.getScriptId())
                .name(entity.getName())
                .toolClassName(entity.getToolClassName())
                .scriptContent(entity.getScriptContent())
                .remark(entity.getRemark())
                .createTime(entity.getCreateTime())
                .updateTime(entity.getUpdateTime())
                .build();
    }
}
