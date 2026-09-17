package com.arbor.vo;

import com.arbor.entity.ProgramScript;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 程序脚本 VO
 *
 * @author gwl
 */
@Data
@Builder
public class ProgramScriptVO {

    private Long id;
    /** 脚本唯一标识 */
    private String scriptId;
    /** 脚本名称 */
    private String name;
    /** 脚本内容（Groovy） */
    private String scriptContent;
    /** 自定义超时秒数 */
    private Integer queryTimeout;
    /** 备注 */
    private String remark;
    /** 创建时间 */
    private LocalDateTime createTime;
    /** 更新时间 */
    private LocalDateTime updateTime;

    public static ProgramScriptVO fromEntity(ProgramScript entity) {
        return ProgramScriptVO.builder()
                .id(entity.getId())
                .scriptId(entity.getScriptId())
                .name(entity.getName())
                .scriptContent(entity.getScriptContent())
                .queryTimeout(entity.getQueryTimeout())
                .remark(entity.getRemark())
                .createTime(entity.getCreateTime())
                .updateTime(entity.getUpdateTime())
                .build();
    }
}
