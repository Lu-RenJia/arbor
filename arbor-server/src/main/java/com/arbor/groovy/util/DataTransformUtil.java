package com.arbor.groovy.util;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Groovy 脚本可用数据转换工具类
 *
 * @author gwl
 */
public class DataTransformUtil {

    /** 提取对象列表中的某个字段值列表 */
    @SuppressWarnings("unchecked")
    public static List<Object> pluck(List<?> list, String field) {
        if (list == null || list.isEmpty()) return Collections.emptyList();
        return list.stream()
                .map(item -> {
                    if (item instanceof Map) return ((Map<String, Object>) item).get(field);
                    try {
                        return item.getClass().getMethod("get" + capitalize(field)).invoke(item);
                    } catch (Exception e) {
                        return null;
                    }
                })
                .collect(Collectors.toList());
    }

    /** 将 List<Map> 按某个字段分组 */
    @SuppressWarnings("unchecked")
    public static Map<Object, List<Map<String, Object>>> groupBy(List<Map<String, Object>> list, String field) {
        return list.stream().collect(Collectors.groupingBy(m -> m.get(field)));
    }

    /** 合并多个 List */
    @SafeVarargs
    public static <T> List<T> merge(List<T>... lists) {
        List<T> result = new ArrayList<>();
        for (List<T> list : lists) {
            if (list != null) result.addAll(list);
        }
        return result;
    }

    private static String capitalize(String str) {
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }
}
