package com.arbor.vo;

import lombok.Data;

/**
 * 应用共享记录（「我的应用」页展示某应用已共享到哪些广场、并支持逐个移除用）
 * <p>扁平结构：同一应用共享到同一广场的多个环境会有多行，前端按 marketId 分组展示。</p>
 */
@Data
public class AppShareVO {
    /** 广场ID */
    private Long marketId;
    /** 广场名称（JOIN填充） */
    private String marketName;
    /** 环境ID */
    private Long envId;
    /** 共享操作人ID（前端据此门控移除按钮：仅共享者本人或管理员可移除） */
    private Long sharedBy;
}
