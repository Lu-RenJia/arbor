package com.arbor.engine;

import com.arbor.entity.FileResource;
import com.arbor.service.FileResourceService;
import com.arbor.util.ExcelUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * FILE 输出模式处理器 — 支持脚本指定 fileType + data 协议，兼容旧 List&lt;Map&gt; 自动 Excel
 *
 * @author gwl
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FileOutputHandler {

    private final FileResourceService fileResourceService;

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /** 文件结果标记 key（API 节点二进制响应/自动检测后的节点 data 摘要结构） */
    public static final String FILE_MARKER_KEY = "__file";

    /**
     * 如果 START 节点配置了 outputType=FILE，取最后业务节点数据按协议输出文件。
     *
     * @param nodeResultList 节点执行结果列表
     * @param startConfig    START 节点配置 Map
     * @param flowId         流程 ID
     * @param flowName       流程名称
     * @return 输出文件信息，不满足条件时返回 null
     */
    @SuppressWarnings("unchecked")
    public FlowExecutionResult.OutputFileInfo handleFileOutput(
            List<NodeResult> nodeResultList,
            Map<String, Object> startConfig,
            Long flowId,
            String flowName) {
        if (startConfig == null) return null;
        if (!"FILE".equals(String.valueOf(startConfig.getOrDefault("outputType", "JSON")))) return null;

        NodeResult lastDataNode = findLastBizNode(nodeResultList);
        if (lastDataNode == null) {
            log.warn("FILE 输出模式: 未找到可导出数据的业务节点");
            return null;
        }

        Object data = lastDataNode.getData();
        try {
            // === 新协议：Map 含 fileType + data ===
            if (data instanceof Map) {
                Map<String, Object> map = (Map<String, Object>) data;
                Object ft = map.get("fileType");
                Object fd = map.get("data");
                Object fn = map.get("fileName");
                if (ft != null && fd != null) {
                    FlowExecutionResult.OutputFileInfo info = handleFileType(ft.toString(), fd, fn != null ? fn.toString() : null, flowId, flowName);
                    // 协议 Map 可能含 base64 大字段，生成文件后用摘要替换，避免前端/日志展示
                    if (info != null) replaceDataWithSummary(lastDataNode, info);
                    return info;
                }
            }

            // === 兼容旧写法：List<Map> 自动 Excel ===
            if (data instanceof List) {
                List<?> list = (List<?>) data;
                if (!list.isEmpty() && list.get(0) instanceof Map) {
                    return exportExcel((List<Map<String, Object>>) list, null, flowId, flowName);
                }
                log.warn("FILE 输出模式: List 元素不是 Map, 跳过导出");
                return null;
            }

            log.warn("FILE 输出模式: 不支持的数据类型, type={}", data.getClass().getSimpleName());
            return null;
        } catch (Exception e) {
            log.error("FILE 输出模式: 导出失败", e);
            return null;
        }
    }

    // ===================== 类型分发 =====================

    private FlowExecutionResult.OutputFileInfo handleFileType(String fileType, Object data,
                                                               String fileName, Long flowId, String flowName) throws Exception {
        return switch (fileType.toUpperCase()) {
            case "TXT" -> exportText(data.toString(), fileName, flowId, flowName);
            case "EXCEL" -> {
                if (!(data instanceof List)) throw new IllegalArgumentException("EXCEL 的 data 必须是 List<Map>");
                yield exportExcel((List<Map<String, Object>>) data, fileName, flowId, flowName);
            }
            case "XML" -> exportXml(data.toString(), fileName, flowId, flowName);
            case "BINARY" -> exportBinary(data.toString(), fileName, flowId, flowName);
            default -> {
                log.warn("FILE 输出模式: 未知 fileType={}, 跳过", fileType);
                yield null;
            }
        };
    }

    // ===================== 自动检测（不依赖 outputType=FILE） =====================

    /**
     * 自动检测最后业务节点的文件结果（outputType=JSON 模式兑底）：
     * 1) API 节点二进制响应标记 {__file:true, fileId, fileName, size} —— 文件已落库，直接采用；
     * 2) 脚本协议 {fileType, data, fileName}（含 BINARY base64）—— 按协议生成文件。
     * 生成/命中后节点 data 已是摘要结构，前端不会展示二进制/base64。
     */
    @SuppressWarnings("unchecked")
    public FlowExecutionResult.OutputFileInfo detectFileOutput(List<NodeResult> nodeResultList, Long flowId, String flowName) {
        NodeResult last = findLastBizNode(nodeResultList);
        if (last == null || !(last.getData() instanceof Map)) return null;
        Map<String, Object> map = (Map<String, Object>) last.getData();
        try {
            // API 节点二进制响应标记：文件已由执行器落库（size 为可读文本，兼容旧数字格式）
            if (Boolean.TRUE.equals(map.get(FILE_MARKER_KEY)) && map.get("fileId") instanceof Number fid) {
                Object sizeVal = map.get("size");
                return FlowExecutionResult.OutputFileInfo.builder()
                        .fileId(fid.longValue())
                        .fileName(map.get("fileName") != null ? map.get("fileName").toString() : null)
                        .size(sizeVal instanceof Number n ? n.longValue() : null)
                        .sizeText(sizeVal instanceof Number n ? formatSize(n.longValue())
                                : sizeVal != null ? sizeVal.toString() : null)
                        .build();
            }
            // 脚本协议：fileType + data
            Object ft = map.get("fileType");
            Object fd = map.get("data");
            Object fn = map.get("fileName");
            if (ft != null && fd != null) {
                FlowExecutionResult.OutputFileInfo info = handleFileType(ft.toString(), fd,
                        fn != null ? fn.toString() : null, flowId, flowName);
                if (info != null) replaceDataWithSummary(last, info);
                return info;
            }
        } catch (Exception e) {
            log.error("文件结果自动检测失败", e);
        }
        return null;
    }

    /** 将二进制字节存为输出文件（API 节点二进制响应/脚本 BINARY 共用） */
    public FlowExecutionResult.OutputFileInfo saveBinary(byte[] bytes, String fileName, String contentType, Long flowId) {
        try {
            String name = (fileName != null && !fileName.isBlank()) ? fileName
                    : "download_" + LocalDateTime.now().format(TS) + ".bin";
            String ct = (contentType != null && !contentType.isBlank()) ? contentType : "application/octet-stream";
            FileResource fr = fileResourceService.createOutput(flowId, name,
                    new ByteArrayInputStream(bytes), ct, bytes.length);
            log.info("FILE输出[BINARY]: fileId={}, fileName={}, size={}", fr.getId(), name, bytes.length);
            return toOutputInfo(fr);
        } catch (Exception e) {
            log.error("二进制文件保存失败", e);
            return null;
        }
    }

    /** 构造节点 data 的文件标记结构（API 执行器返回文件结果时使用），size 为换算单位后的可读文本 */
    public static Map<String, Object> fileMarker(FlowExecutionResult.OutputFileInfo info) {
        Map<String, Object> marker = new java.util.LinkedHashMap<>();
        marker.put(FILE_MARKER_KEY, true);
        marker.put("fileId", info.getFileId());
        marker.put("fileName", info.getFileName());
        marker.put("size", formatSize(info.getSize()));
        return marker;
    }

    /** 字节数换算为可读单位（B / KB / MB / GB），保留最多两位小数 */
    public static String formatSize(Long bytes) {
        if (bytes == null) return null;
        if (bytes < 1024) return bytes + " B";
        double value = bytes / 1024.0;
        String unit = "KB";
        if (value >= 1024) { value /= 1024; unit = "MB"; }
        if (value >= 1024) { value /= 1024; unit = "GB"; }
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString() + " " + unit;
    }

    /** 将节点 data 替换为文件摘要，避免展示二进制/base64 */
    private static void replaceDataWithSummary(NodeResult node, FlowExecutionResult.OutputFileInfo info) {
        node.setData(fileMarker(info));
    }

    // ===================== 格式导出 =====================

    /** 确保文件名带有正确的扩展名 */
    private static String ensureExt(String name, String ext) {
        if (name == null || name.isBlank()) return null;
        return name.endsWith(ext) ? name : name + ext;
    }

    private FlowExecutionResult.OutputFileInfo exportText(String text, String customName, Long flowId, String flowName) throws Exception {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        String fileName = ensureExt(customName, ".txt") != null ? ensureExt(customName, ".txt")
                : flowName + "_" + LocalDateTime.now().format(TS) + ".txt";
        FileResource fr = fileResourceService.createOutput(flowId, fileName,
                new ByteArrayInputStream(bytes), "text/plain; charset=UTF-8", bytes.length);
        log.info("FILE输出[TXT]: fileId={}, fileName={}, size={}", fr.getId(), fileName, bytes.length);
        return toOutputInfo(fr);
    }

    private FlowExecutionResult.OutputFileInfo exportExcel(List<Map<String, Object>> data, String customName,
                                                            Long flowId, String flowName) throws Exception {
        byte[] bytes = ExcelUtil.exportMapsToBytes(data);
        String fileName = ensureExt(customName, ".xlsx") != null ? ensureExt(customName, ".xlsx")
                : flowName + "_" + LocalDateTime.now().format(TS) + ".xlsx";
        FileResource fr = fileResourceService.createOutput(flowId, fileName,
                new ByteArrayInputStream(bytes),
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes.length);
        log.info("FILE输出[EXCEL]: fileId={}, fileName={}, size={}", fr.getId(), fileName, bytes.length);
        return toOutputInfo(fr);
    }

    private FlowExecutionResult.OutputFileInfo exportXml(String xml, String customName, Long flowId, String flowName) throws Exception {
        byte[] bytes = xml.getBytes(StandardCharsets.UTF_8);
        String fileName = ensureExt(customName, ".xml") != null ? ensureExt(customName, ".xml")
                : flowName + "_" + LocalDateTime.now().format(TS) + ".xml";
        FileResource fr = fileResourceService.createOutput(flowId, fileName,
                new ByteArrayInputStream(bytes), "application/xml; charset=UTF-8", bytes.length);
        log.info("FILE输出[XML]: fileId={}, fileName={}, size={}", fr.getId(), fileName, bytes.length);
        return toOutputInfo(fr);
    }

    /** BINARY：data 为 base64 字符串（兼容 data:xxx;base64, 前缀），解码后直接落库 */
    private FlowExecutionResult.OutputFileInfo exportBinary(String base64, String customName, Long flowId, String flowName) throws Exception {
        String b64 = base64.contains(",") ? base64.substring(base64.indexOf(',') + 1) : base64;
        byte[] bytes = java.util.Base64.getDecoder().decode(b64.trim());
        String fileName = (customName != null && !customName.isBlank()) ? customName
                : flowName + "_" + LocalDateTime.now().format(TS) + ".bin";
        FileResource fr = fileResourceService.createOutput(flowId, fileName,
                new ByteArrayInputStream(bytes), guessContentType(fileName), bytes.length);
        log.info("FILE输出[BINARY]: fileId={}, fileName={}, size={}", fr.getId(), fileName, bytes.length);
        return toOutputInfo(fr);
    }

    /** 按扩展名推断 Content-Type，未知按通用二进制流 */
    private static String guessContentType(String fileName) {
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".pdf")) return "application/pdf";
        if (lower.endsWith(".zip")) return "application/zip";
        if (lower.endsWith(".xlsx")) return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        if (lower.endsWith(".xls")) return "application/vnd.ms-excel";
        if (lower.endsWith(".csv")) return "text/csv; charset=UTF-8";
        if (lower.endsWith(".txt")) return "text/plain; charset=UTF-8";
        if (lower.endsWith(".json")) return "application/json";
        if (lower.endsWith(".xml")) return "application/xml; charset=UTF-8";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".gif")) return "image/gif";
        return "application/octet-stream";
    }

    // ===================== 工具 =====================

    private static FlowExecutionResult.OutputFileInfo toOutputInfo(FileResource fr) {
        return FlowExecutionResult.OutputFileInfo.builder()
                .fileName(fr.getFileName()).fileId(fr.getId()).size(fr.getFileSize())
                .sizeText(formatSize(fr.getFileSize())).build();
    }

    private static NodeResult findLastBizNode(List<NodeResult> list) {
        for (int i = list.size() - 1; i >= 0; i--) {
            NodeResult nr = list.get(i);
            if (nr.isSuccess() && nr.getData() != null
                    && !"START".equals(nr.getNodeType())
                    && !"END".equals(nr.getNodeType())
                    && !"PARAM".equals(nr.getNodeType())) {
                String code = nr.getNodeCode();
                if (code != null && !code.startsWith("_")) return nr;
            }
        }
        return null;
    }
}
