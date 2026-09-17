package com.arbor.vo;

import com.arbor.entity.RedisDatasource;
import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class RedisDatasourceVO {
    private Long id;
    /** 名称 */
    private String name;
    /** 主机地址 */
    private String host;
    /** 端口 */
    private Integer port;
    /** 密码脱敏提示（不返明码） */
    private String passwordHint;
    /** 数据库索引 */
    private Integer db;
    /** 备注 */
    private String remark;
    /** 创建时间 */
    private LocalDateTime createTime;
    /** 更新时间 */
    private LocalDateTime updateTime;

    public static RedisDatasourceVO fromEntity(RedisDatasource e) {
        return builder().id(e.getId()).name(e.getName()).host(e.getHost()).port(e.getPort())
                .passwordHint(e.getPassword() != null && !e.getPassword().isEmpty() ? "******" : "")
                .db(e.getDb()).remark(e.getRemark())
                .createTime(e.getCreateTime()).updateTime(e.getUpdateTime()).build();
    }
}
