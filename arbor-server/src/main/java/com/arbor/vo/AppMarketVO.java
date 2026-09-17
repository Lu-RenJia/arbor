package com.arbor.vo;

import com.arbor.entity.AppMarket;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class AppMarketVO {
    private Long id;
    /** 广场名称 */
    private String name;
    /** 备注 */
    private String remark;
    /** 创建人ID */
    private Long createBy;
    /** 创建时间 */
    private LocalDateTime createTime;
    /** 更新时间 */
    private LocalDateTime updateTime;

    public static AppMarketVO fromEntity(AppMarket e) {
        return AppMarketVO.builder()
            .id(e.getId()).name(e.getName()).remark(e.getRemark())
            .createBy(e.getCreateBy()).createTime(e.getCreateTime()).updateTime(e.getUpdateTime()).build();
    }
}
