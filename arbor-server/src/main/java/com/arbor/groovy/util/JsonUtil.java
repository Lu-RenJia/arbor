package com.arbor.groovy.util;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;

/**
 * Groovy 脚本可用 JSON 工具类
 *
 * @author gwl
 */
public class JsonUtil {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 对象转 JSON 字符串 */
    public static String toJson(Object obj) {
        try {
            return MAPPER.writeValueAsString(obj);
        } catch (Exception e) {
            throw new RuntimeException("JSON序列化失败", e);
        }
    }

    /** JSON 字符串转 Map */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> fromJson(String json) {
        try {
            return MAPPER.readValue(json, Map.class);
        } catch (Exception e) {
            throw new RuntimeException("JSON解析失败", e);
        }
    }

    /** JSON 美化 */
    public static String prettyPrint(String json) {
        try {
            Object obj = MAPPER.readValue(json, Object.class);
            return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(obj);
        } catch (Exception e) {
            return json;
        }
    }
}
