package com.arbor.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 部门 DTO
 *
 * @author gwl
 */
@Data
public class DepartmentDTO {

    /** 部门名称 */
    @NotBlank(message = "部门名称不能为空")
    private String name;

    /** 上级部门ID */
    private Long parentId;

    /** 备注 */
    private String remark;

    /** 负责人 */
    private String leader;

    /** 联系电话 */
    private String phone;

    /** 邮箱 */
    private String email;

    /** 排序号 */
    private Integer sortOrder;

    /** 状态 */
    private String status;
}
