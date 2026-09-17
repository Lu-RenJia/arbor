package com.arbor.groovy.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Groovy 脚本可用日期工具类
 *
 * @author gwl
 */
public class DateUtil {

    private static final DateTimeFormatter DEFAULT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 获取当前日期时间字符串 */
    public static String now() {
        return LocalDateTime.now().format(DEFAULT_FMT);
    }

    /** 格式化日期 */
    public static String format(LocalDateTime dateTime, String pattern) {
        return dateTime.format(DateTimeFormatter.ofPattern(pattern));
    }

    /** 解析日期字符串 */
    public static LocalDateTime parse(String dateStr) {
        return LocalDateTime.parse(dateStr, DEFAULT_FMT);
    }
}
