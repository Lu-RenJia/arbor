package com.arbor.service;

import com.arbor.common.AssertUtils;
import com.arbor.common.BusinessException;
import com.arbor.common.ErrorCode;
import com.arbor.common.TenantContext;
import com.arbor.dto.ToolClassDTO;
import com.arbor.entity.ProgramScript;
import com.arbor.groovy.GroovyScriptEngine;
import com.arbor.mapper.ProgramScriptMapper;
import com.arbor.vo.ToolClassVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 工具类服务
 * <p>
 * 全局共享的 Groovy 工具类管理：仅 admin 可创建/修改/删除，所有用户可查看和使用。
 * 保存时同步编译校验，启动时批量预编译注入缓存。
 * </p>
 *
 * @author gwl
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ToolClassService implements ApplicationRunner {

    /** scriptId 前缀 */
    private static final String SCRIPT_ID_PREFIX = "TC";

    /** 工具类数量警告阈值 */
    private static final int TOOL_COUNT_WARNING_THRESHOLD = 50;

    /** 类名解析正则：匹配 class ClassName（支持 extends/implements） */
    private static final Pattern CLASS_NAME_PATTERN = Pattern.compile(
            "(?:public\\s+)?class\\s+(\\w+)");

    private final ProgramScriptMapper mapper;
    private final GroovyScriptEngine groovyScriptEngine;

    /**
     * 启动时批量预编译所有工具类
     */
    @Override
    public void run(ApplicationArguments args) {
        preloadToolClasses();
    }

    /**
     * 预编译所有工具类到缓存
     */
    public void preloadToolClasses() {
        TenantContext.skipTenant(true);
        try {
            List<ProgramScript> tools = mapper.selectToolList();
            int success = 0;
            int failed = 0;
            for (ProgramScript tool : tools) {
                try {
                    groovyScriptEngine.defineToolClass(tool.getToolClassName(), tool.getScriptContent());
                    success++;
                } catch (Exception e) {
                    failed++;
                    log.error("工具类预编译失败: className={}, error={}",
                            tool.getToolClassName(), e.getMessage());
                }
            }
            log.info("工具类预编译完成: 总数={}, 成功={}, 失败={}", tools.size(), success, failed);
        } finally {
            TenantContext.skipTenant(false);
        }
    }

    /**
     * 查询所有工具类列表（所有用户可调用）
     */
    public List<ToolClassVO> listTools() {
        TenantContext.skipTenant(true);
        try {
            List<ProgramScript> tools = mapper.selectToolList();
            return tools.stream().map(ToolClassVO::fromEntity).collect(Collectors.toList());
        } finally {
            TenantContext.skipTenant(false);
        }
    }

    /**
     * 查看工具类详情
     */
    public ToolClassVO getTool(Long id) {
        TenantContext.skipTenant(true);
        try {
            ProgramScript entity = mapper.selectById(id);
            AssertUtils.notNull(entity, ErrorCode.PROGRAM_SCRIPT_NOT_FOUND, id);
            AssertUtils.isTrue(Integer.valueOf(1).equals(entity.getIsTool()),
                    ErrorCode.PROGRAM_SCRIPT_NOT_FOUND, id);
            return ToolClassVO.fromEntity(entity);
        } finally {
            TenantContext.skipTenant(false);
        }
    }

    /**
     * 创建工具类（仅 admin）
     *
     * @return 创建结果，message 中可能包含数量警告
     */
    @Transactional
    public ToolClassVO createTool(ToolClassDTO dto) {
        assertAdmin();

        TenantContext.skipTenant(true);
        try {
            // 1. 解析类名
            String className = parseClassName(dto.getScriptContent());

            // 2. 类名查重
            checkClassNameUnique(className, null);

            // 3. 同步编译校验（失败抛异常，不写DB）
            compileAndValidate(className, dto.getScriptContent());

            // 4. 写入 DB
            ProgramScript entity = new ProgramScript();
            entity.setScriptId(generateScriptId());
            entity.setName(dto.getName());
            entity.setScriptContent(dto.getScriptContent());
            entity.setRemark(dto.getRemark());
            entity.setIsTool(1);
            entity.setToolClassName(className);
            entity.setUid(java.util.UUID.randomUUID().toString());
            mapper.insertTool(entity);

            // 5. 注入编译缓存
            groovyScriptEngine.defineToolClass(className, dto.getScriptContent());

            log.info("创建工具类: name={}, className={}, scriptId={}", dto.getName(), className, entity.getScriptId());
            return ToolClassVO.fromEntity(entity);
        } finally {
            TenantContext.skipTenant(false);
        }
    }

    /**
     * 更新工具类（仅 admin）
     */
    @Transactional
    public ToolClassVO updateTool(Long id, ToolClassDTO dto) {
        assertAdmin();

        TenantContext.skipTenant(true);
        try {
            ProgramScript entity = mapper.selectById(id);
            AssertUtils.notNull(entity, ErrorCode.PROGRAM_SCRIPT_NOT_FOUND, id);
            AssertUtils.isTrue(Integer.valueOf(1).equals(entity.getIsTool()),
                    ErrorCode.PROGRAM_SCRIPT_NOT_FOUND, id);

            // 1. 解析新类名
            String newClassName = parseClassName(dto.getScriptContent());

            // 2. 类名查重（排除自身）
            checkClassNameUnique(newClassName, id);

            // 3. 同步编译校验（失败抛异常，不写DB；旧版本缓存保持不动，脚本仍可用旧工具类）
            compileAndValidate(newClassName, dto.getScriptContent());

            // 4. 更新 DB。旧类名必须在 setToolClassName 覆盖前留存，改名场景下才能驱逐到旧缓存
            String oldClassName = entity.getToolClassName();
            entity.setName(dto.getName());
            entity.setScriptContent(dto.getScriptContent());
            entity.setRemark(dto.getRemark());
            entity.setToolClassName(newClassName);
            mapper.updateTool(entity);

            // 5. DB 落库成功后才驱逐旧缓存并重建。
            //    evictToolClass 会连带作废所有脚本编译产物（脚本字节码固化了工具类 Class 引用），
            //    故必须放在 DB 成功之后：否则 DB 写失败回滚时缓存已被清空，引用该工具类的脚本
            //    会全部报错，且 rebuildClassLoader/preloadToolClasses 均无自动触发时机，只能重启恢复。
            groovyScriptEngine.evictToolClass(oldClassName);
            groovyScriptEngine.defineToolClass(newClassName, dto.getScriptContent());

            log.info("更新工具类: id={}, className={}", id, newClassName);
            return ToolClassVO.fromEntity(entity);
        } finally {
            TenantContext.skipTenant(false);
        }
    }

    /**
     * 删除工具类（仅 admin）
     */
    @Transactional
    public void deleteTool(Long id) {
        assertAdmin();

        TenantContext.skipTenant(true);
        try {
            ProgramScript entity = mapper.selectById(id);
            AssertUtils.notNull(entity, ErrorCode.PROGRAM_SCRIPT_NOT_FOUND, id);
            AssertUtils.isTrue(Integer.valueOf(1).equals(entity.getIsTool()),
                    ErrorCode.PROGRAM_SCRIPT_NOT_FOUND, id);

            // 删除 DB
            mapper.deleteById(id);

            // 驱逐缓存
            groovyScriptEngine.evictToolClass(entity.getToolClassName());

            log.info("删除工具类: id={}, className={}", id, entity.getToolClassName());
        } finally {
            TenantContext.skipTenant(false);
        }
    }

    /**
     * 获取当前工具类总数
     */
    public int getToolCount() {
        TenantContext.skipTenant(true);
        try {
            return mapper.countTools();
        } finally {
            TenantContext.skipTenant(false);
        }
    }

    /**
     * 获取数量警告阈值
     */
    public int getWarningThreshold() {
        return TOOL_COUNT_WARNING_THRESHOLD;
    }

    // ====== 私有方法 ======

    /**
     * 校验当前用户是否为 admin
     */
    private void assertAdmin() {
        if (!TenantContext.isAdmin()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅管理员可管理工具类");
        }
    }

    /**
     * 从 Groovy 源码中解析类名
     */
    private String parseClassName(String source) {
        Matcher matcher = CLASS_NAME_PATTERN.matcher(source);
        if (!matcher.find()) {
            throw new BusinessException(ErrorCode.GROOVY_SCRIPT_FAILED,
                    "无法解析类名：工具类代码必须包含 class 定义（如 class MyUtil { ... }）");
        }
        return matcher.group(1);
    }

    /**
     * 校验类名全局唯一（调用方需已设置 skipTenant）
     *
     * @param className 类名
     * @param excludeId 排除的记录ID（更新时排除自身）
     */
    private void checkClassNameUnique(String className, Long excludeId) {
        ProgramScript existing = mapper.selectByToolClassName(className);
        if (existing != null && !existing.getId().equals(excludeId)) {
            throw new BusinessException(ErrorCode.GROOVY_SCRIPT_FAILED,
                    "类名 \"" + className + "\" 已存在，请使用其他类名");
        }
    }

    /**
     * 同步编译校验：编译失败则抛出异常（含友好错误信息）
     * <p>
     * 失败时不需要驱逐缓存：defineToolClass 用 computeIfAbsent 写入，编译抛异常不会留下条目；
     * 且更新场景下旧版本仍在缓存中，保留它可让脚本继续正常运行，与事务回滚后的 DB 状态一致。
     * 若在此调用 evictToolClass，反而会连带作废全部脚本编译缓存——一次语法错误的保存
     * 不应导致全站脚本重编译。
     * </p>
     */
    private void compileAndValidate(String className, String source) {
        try {
            groovyScriptEngine.defineToolClass(className, source);
        } catch (Exception e) {
            String msg = e.getMessage();
            throw new BusinessException(ErrorCode.GROOVY_SCRIPT_FAILED,
                    "工具类编译失败: " + (msg != null ? msg : "未知错误"));
        }
    }

    /**
     * 生成唯一 scriptId
     */
    private String generateScriptId() {
        return SCRIPT_ID_PREFIX + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
    }
}
