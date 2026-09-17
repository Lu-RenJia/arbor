package com.arbor.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 导入执行结果 VO
 *
 * @author gwl
 */
@Data
public class ImportResultVO {

    private int created;
    private int updated;
    private int skipped;

    /** 逐项处理明细 */
    private List<String> details = new ArrayList<>();

    /** 警告（缺失数据源、失效引用等） */
    private List<String> warnings = new ArrayList<>();
}
