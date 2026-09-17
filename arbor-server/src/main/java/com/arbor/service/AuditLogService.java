package com.arbor.service;

import com.arbor.common.PageResult;
import com.arbor.entity.AuditLog;
import com.arbor.mapper.AuditLogMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 操作审计日志服务 — 写入失败仅记日志，绝不影响业务主流程
 *
 * @author gwl
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogMapper auditLogMapper;
    private final ObjectMapper objectMapper;

    /** detail 最大长度，防止大 body 撑爆字段 */
    private static final int DETAIL_MAX_LENGTH = 2000;
    /** 需要脱敏的字段名片段（小写比较） */
    private static final String[] SENSITIVE_FIELDS = {"password", "secret"};

    /** 安全写入：任何异常只记 warn，不向上抛 */
    public void recordSafely(AuditLog auditLog) {
        try {
            if (auditLog.getCreateTime() == null) {
                auditLog.setCreateTime(LocalDateTime.now());
            }
            auditLogMapper.insert(auditLog);
        } catch (Exception e) {
            log.warn("审计日志写入失败（不影响业务）: module={}, action={}, username={}",
                    auditLog.getModuleName(), auditLog.getAction(), auditLog.getUsername(), e);
        }
    }

    /** 补充当前请求上下文（method/uri/ip），无请求上下文时跳过 */
    public void fillRequestContext(AuditLog auditLog) {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            return;
        }
        HttpServletRequest request = attrs.getRequest();
        auditLog.setMethod(request.getMethod());
        auditLog.setUri(request.getRequestURI());
        auditLog.setIp(resolveIp(request));
    }

    /** 构建请求参数快照：序列化方法入参并对敏感字段脱敏 */
    public String buildDetail(Object[] args) {
        if (args == null || args.length == 0) {
            return null;
        }
        try {
            Map<String, Object> serializable = new LinkedHashMap<>();
            int index = 0;
            for (Object arg : args) {
                index++;
                if (arg == null || isSkippable(arg)) {
                    continue;
                }
                if (arg instanceof MultipartFile file) {
                    serializable.put("file" + index, file.getOriginalFilename() + "(" + file.getSize() + "B)");
                    continue;
                }
                serializable.put("arg" + index, arg);
            }
            if (serializable.isEmpty()) {
                return null;
            }
            JsonNode tree = objectMapper.valueToTree(serializable);
            maskSensitive(tree);
            String json = objectMapper.writeValueAsString(tree);
            return json.length() > DETAIL_MAX_LENGTH
                    ? json.substring(0, DETAIL_MAX_LENGTH) + "...(已截断)"
                    : json;
        } catch (Exception e) {
            log.debug("审计参数序列化失败: {}", e.getMessage());
            return null;
        }
    }

    private boolean isSkippable(Object arg) {
        return arg instanceof HttpServletRequest
                || arg instanceof HttpServletResponse
                || arg instanceof org.springframework.validation.BindingResult;
    }

    /** 递归脱敏：字段名含 password/secret 的值替换为 ****** */
    private void maskSensitive(JsonNode node) {
        if (node instanceof ObjectNode obj) {
            Iterator<Map.Entry<String, JsonNode>> fields = obj.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                String name = entry.getKey().toLowerCase();
                boolean sensitive = false;
                for (String s : SENSITIVE_FIELDS) {
                    if (name.contains(s)) {
                        sensitive = true;
                        break;
                    }
                }
                if (sensitive && entry.getValue().isValueNode()) {
                    obj.put(entry.getKey(), "******");
                } else {
                    maskSensitive(entry.getValue());
                }
            }
        } else if (node.isArray()) {
            node.forEach(this::maskSensitive);
        }
    }

    /** 解析真实来源IP（支持 nginx 反代的 X-Forwarded-For） */
    public static String resolveIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            int comma = xff.indexOf(',');
            return (comma > 0 ? xff.substring(0, comma) : xff).trim();
        }
        String real = request.getHeader("X-Real-IP");
        if (real != null && !real.isBlank()) {
            return real;
        }
        return request.getRemoteAddr();
    }

    /** 分页查询审计日志 */
    public PageResult<AuditLog> page(int pageNum, int pageSize, String moduleName, String username, String keyword) {
        long total = auditLogMapper.countPage(moduleName, username, keyword);
        var list = auditLogMapper.selectPage((long) (pageNum - 1) * pageSize, pageSize, moduleName, username, keyword);
        return PageResult.of(pageNum, pageSize, total, list);
    }
}
