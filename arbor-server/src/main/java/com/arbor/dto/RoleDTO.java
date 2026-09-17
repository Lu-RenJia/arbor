package com.arbor.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 角色 DTO
 *
 * @author gwl
 */
@Data
public class RoleDTO {

    /** 角色编码 */
    @NotBlank(message = "角色编码不能为空")
    private String code;

    /** 角色名称 */
    @NotBlank(message = "角色名称不能为空")
    private String name;

    /** 角色权限标识 */
    private String roleKey;

    /** 备注 */
    private String remark;

    /** 排序号 */
    private Integer roleSort;

    /** 状态 */
    private String status;
}
