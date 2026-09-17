package com.arbor.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class RedisDatasourceDTO {
    /** 已保存的数据源ID（仅「测试连接」用：编辑态未改密码时按它回取库里已存的密码；新增/保存时忽略） */
    private Long id;
    /** 名称 */
    @NotBlank(message = "名称不能为空") private String name;
    /** 主机地址 */
    @NotBlank(message = "主机地址不能为空") private String host;
    /** 端口 */
    @NotNull(message = "端口不能为空") private Integer port;
    /** 密码（明文传入，AES 加密后存储） */
    private String password;
    /** 是否修改密码（仅修改时使用） */
    private Boolean modifyPassword;
    /** 数据库索引 */
    private Integer db;
    /** 备注 */
    private String remark;
}
