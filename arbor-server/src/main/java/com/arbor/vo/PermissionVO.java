package com.arbor.vo;

import com.arbor.entity.Permission;
import lombok.Builder;
import lombok.Data;

/**
 * 权限 VO
 *
 * @author gwl
 */
@Data
@Builder
public class PermissionVO {

    private Long id;
    /** 权限编码 */
    private String code;
    /** 权限名称 */
    private String name;
    /** 备注 */
    private String remark;

    public static PermissionVO fromEntity(Permission entity) {
        return PermissionVO.builder()
                .id(entity.getId())
                .code(entity.getCode())
                .name(entity.getName())
                .remark(entity.getRemark())
                .build();
    }
}
