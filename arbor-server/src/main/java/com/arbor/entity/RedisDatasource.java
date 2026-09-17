package com.arbor.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class RedisDatasource extends BaseEntity {
    /** 名称 */
    private String name;
    /** 主机地址 */
    private String host;
    /** 端口 */
    private Integer port;
    /** 密码（AES 加密） */
    private String password;
    /** 数据库索引 */
    private Integer db;
    /** 备注 */
    private String remark;
    /** 全局唯一标识（导入导出匹配用，应用层创建时生成UUID） */
    private String uid;
}
