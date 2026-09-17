package com.arbor.vo;

import com.arbor.entity.DbScript;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 数据库脚本 VO
 *
 * @author gwl
 */
@Data
@Builder
public class DbScriptVO {

    private Long id;
    /** 脚本名称 */
    private String name;
    /** 关联的数据源ID */
    private Long datasourceId;
    /** 数据源名称（回显用，由 Service 批量填充；查不到表示已被删除） */
    private String datasourceName;
    /** SQL内容 */
    private String sqlContent;
    /** 返回条数限制 */
    private Integer returnLimit;
    /** 自定义超时秒数 */
    private Integer queryTimeout;
    /** 备注 */
    private String remark;
    /** 创建时间 */
    private LocalDateTime createTime;
    /** 更新时间 */
    private LocalDateTime updateTime;

    public static DbScriptVO fromEntity(DbScript entity) {
        return DbScriptVO.builder()
                .id(entity.getId())
                .name(entity.getName())
                .datasourceId(entity.getDatasourceId())
                .sqlContent(entity.getSqlContent())
                .returnLimit(entity.getReturnLimit())
                .queryTimeout(entity.getQueryTimeout())
                .remark(entity.getRemark())
                .createTime(entity.getCreateTime())
                .updateTime(entity.getUpdateTime())
                .build();
    }
}
