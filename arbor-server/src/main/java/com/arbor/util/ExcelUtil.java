package com.arbor.util;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.*;
import java.lang.reflect.Field;
import java.util.*;

/**
 * Excel 解析与导出工具 — 基于 Apache POI
 * <p>
 * 解析：支持 {@link ExcelColumn} 注解按表头名称映射字段。
 * 导出：根据 {@link ExcelColumn} 注解自动生成表头行和列顺序。
 * </p>
 *
 * @author gwl
 */
public final class ExcelUtil {

    private ExcelUtil() {}

    // ===================== 解析 =====================

    /**
     * 解析 Excel 为对象列表（需要 class 上有 @ExcelColumn 注解的字段）
     */
    public static <T> List<T> parse(InputStream in, Class<T> clazz) throws Exception {
        Map<String, Field> fieldMap = buildFieldMap(clazz);
        List<Map<String, Object>> rawList = parseAsMaps(in);
        List<T> result = new ArrayList<>(rawList.size());
        for (Map<String, Object> row : rawList) {
            T obj = clazz.getDeclaredConstructor().newInstance();
            for (Map.Entry<String, Field> entry : fieldMap.entrySet()) {
                String headerName = entry.getKey();
                Field field = entry.getValue();
                Object value = row.get(headerName);
                if (value != null) {
                    field.setAccessible(true);
                    field.set(obj, convertValue(value, field.getType()));
                }
            }
            result.add(obj);
        }
        return result;
    }

    /**
     * 解析 Excel 为 Map 列表（首行作为 key，不传 class）
     */
    public static List<Map<String, Object>> parse(InputStream in) throws Exception {
        return parseAsMaps(in);
    }

    /** 读取 Excel 转换为 List<Map> 的公共逻辑 */
    private static List<Map<String, Object>> parseAsMaps(InputStream in) throws Exception {
        try (Workbook wb = WorkbookFactory.create(in)) {
            Sheet sheet = wb.getSheetAt(0);
            if (sheet.getLastRowNum() < 1) return Collections.emptyList();

            // 首行 = 表头
            Row headerRow = sheet.getRow(0);
            List<String> headers = new ArrayList<>();
            for (int c = 0; c < headerRow.getLastCellNum(); c++) {
                Cell cell = headerRow.getCell(c);
                headers.add(cell != null ? cell.getStringCellValue().trim() : "");
            }

            // 后续行 = 数据
            List<Map<String, Object>> result = new ArrayList<>();
            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;
                Map<String, Object> map = new LinkedHashMap<>();
                for (int c = 0; c < headers.size(); c++) {
                    if (c >= headers.size()) break;
                    String key = headers.get(c);
                    if (key.isEmpty()) continue;
                    Cell cell = row.getCell(c);
                    map.put(key, getCellValue(cell));
                }
                if (!map.isEmpty()) result.add(map);
            }
            return result;
        }
    }

    // ===================== 导出 =====================

    /**
     * 导出数据到 Excel（自动根据 @ExcelColumn 生成表头）
     */
    public static void export(List<?> data, OutputStream out, Class<?> clazz) throws Exception {
        Map<String, Field> fieldMap = buildFieldMap(clazz);
        List<String> headers = new ArrayList<>(fieldMap.keySet()); // 保持 fieldMap 的插入顺序

        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet();
            // 表头行
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.size(); i++) {
                headerRow.createCell(i).setCellValue(headers.get(i));
            }
            // 数据行
            for (int r = 0; r < data.size(); r++) {
                Row row = sheet.createRow(r + 1);
                Object item = data.get(r);
                for (int c = 0; c < headers.size(); c++) {
                    Field field = fieldMap.get(headers.get(c));
                    field.setAccessible(true);
                    Object value = field.get(item);
                    if (value != null) row.createCell(c).setCellValue(String.valueOf(value));
                }
            }
            wb.write(out);
        }
    }

    /** 如果没有 class，直接把 List<Map> 写出来 */
    public static void exportMaps(List<Map<String, Object>> data, OutputStream out) throws Exception {
        if (data == null || data.isEmpty()) return;
        List<String> headers = new ArrayList<>(data.get(0).keySet());

        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet();
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.size(); i++) {
                headerRow.createCell(i).setCellValue(headers.get(i));
            }
            for (int r = 0; r < data.size(); r++) {
                Row row = sheet.createRow(r + 1);
                Map<String, Object> map = data.get(r);
                for (int c = 0; c < headers.size(); c++) {
                    Object value = map.get(headers.get(c));
                    if (value != null) row.createCell(c).setCellValue(String.valueOf(value));
                }
            }
            wb.write(out);
        }
    }

    /**
     * 导出 List&lt;Map&gt; 为 Excel 字节数组（供引擎自动导出使用）
     */
    @SuppressWarnings("unchecked")
    public static byte[] exportMapsToBytes(List<?> data) throws Exception {
        if (data == null || data.isEmpty()) return new byte[0];
        // 检查是否为 List&lt;Map&gt;
        Object first = data.get(0);
        if (first instanceof Map) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            exportMaps((List<Map<String, Object>>) data, baos);
            return baos.toByteArray();
        }
        // fallback: 单列输出
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet();
            Row headerRow = sheet.createRow(0);
            headerRow.createCell(0).setCellValue("value");
            for (int r = 0; r < data.size(); r++) {
                Row row = sheet.createRow(r + 1);
                Object val = data.get(r);
                if (val != null) row.createCell(0).setCellValue(String.valueOf(val));
            }
            wb.write(baos);
        }
        return baos.toByteArray();
    }

    // ===================== 内部工具 =====================

    /** 构建表头名称 → Field 映射（保持声明顺序） */
    private static Map<String, Field> buildFieldMap(Class<?> clazz) {
        Map<String, Field> map = new LinkedHashMap<>();
        for (Field field : clazz.getDeclaredFields()) {
            ExcelColumn col = field.getAnnotation(ExcelColumn.class);
            if (col != null) map.put(col.name(), field);
        }
        return map;
    }

    /** 读取单元格值 */
    private static Object getCellValue(Cell cell) {
        if (cell == null) return null;
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> DateUtil.isCellDateFormatted(cell) ? cell.getLocalDateTimeCellValue() : cell.getNumericCellValue();
            case BOOLEAN -> cell.getBooleanCellValue();
            case FORMULA -> {
                try {
                    yield cell.getStringCellValue();
                } catch (Exception e) {
                    yield cell.getNumericCellValue();
                }
            }
            default -> null;
        };
    }

    /** 简单类型转换 */
    private static Object convertValue(Object value, Class<?> targetType) {
        if (value == null || targetType.isInstance(value)) return value;
        String s = String.valueOf(value);
        if (targetType == String.class) return s;
        if (targetType == Integer.class || targetType == int.class) return Integer.parseInt(s.replaceAll("\\.0$", ""));
        if (targetType == Long.class || targetType == long.class) return Long.parseLong(s.replaceAll("\\.0$", ""));
        if (targetType == Double.class || targetType == double.class) return Double.parseDouble(s);
        if (targetType == java.math.BigDecimal.class) return new java.math.BigDecimal(s);
        return value;
    }
}
