package com.arbor.service;

import com.arbor.common.AssertUtils;
import com.arbor.common.BusinessException;
import com.arbor.common.ErrorCode;
import com.arbor.dto.RedisKeyPrefixDTO;
import com.arbor.entity.Flow;
import com.arbor.entity.FlowNode;
import com.arbor.entity.RedisKeyPrefix;
import com.arbor.entity.RedisScript;
import com.arbor.mapper.FlowMapper;
import com.arbor.mapper.FlowNodeMapper;
import com.arbor.mapper.RedisDatasourceMapper;
import com.arbor.mapper.RedisKeyPrefixMapper;
import com.arbor.mapper.RedisScriptMapper;
import com.arbor.vo.RedisKeyPrefixVO;
import com.arbor.vo.RedisPrefixUsageVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Redis 业务前缀服务（D1/D5/D8/D11）
 * <p>
 * 前缀承担 key 的层级管理与溯源职责，取代原先由系统强加的
 * {@code env:{envId}:flow:redis:user:{uid}:flow:{flowId}:} 四层前缀。
 * 最终 key = {@code {prefix}:{用户填的 redisKey}}。
 * </p>
 * <p>
 * {@code redis_key_prefix} 未加入 {@code MybatisPlusConfig.SHARED_TABLES}，
 * 所有查询自动受租户过滤（全员隔离，管理员也只看自己的），故本类无需额外权限判定。
 * </p>
 *
 * @author gwl
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RedisKeyPrefixService {

    /** REDIS 节点在 flow_node.config 里引用 redis_script 的 JSON 字段名（存数字主键，已实测确认） */
    private static final String NODE_TYPE_REDIS = "REDIS";
    private static final String CONFIG_KEY_REDIS_ID = "redisConfigId";

    /** 前缀最大长度，对齐 DB 列 varchar(100) */
    private static final int PREFIX_MAX_LEN = 100;

    /**
     * 前缀禁止出现的字符：空白 + glob 通配符。
     * <p>
     * 通配符在 Redis key 里本身合法，但运维按模式清理缓存（KEYS/SCAN、
     * {@code RedisCacheUtil.evictByPattern}）时会把它当通配符解释而误伤其它 key，
     * 故在登记阶段就禁掉。
     * </p>
     */
    private static final Pattern FORBIDDEN_PREFIX_CHARS = Pattern.compile("[\\s*?\\[\\]]");

    private final RedisKeyPrefixMapper mapper;
    private final RedisDatasourceMapper redisDatasourceMapper;
    private final RedisScriptMapper redisScriptMapper;
    private final FlowNodeMapper flowNodeMapper;
    private final FlowMapper flowMapper;

    // ==================== CRUD ====================

    /**
     * 某数据源下登记的全部前缀（不带计数）。
     * <p>
     * 供 Redis 配置弹窗的前缀下拉使用：下拉只需要 id/prefix/name，而计数每行都要额外扫
     * {@code redis_script} 与 {@code flow_node}（N+1），放在下拉路径上是纯浪费。
     * 管理页要计数请走 {@link #listUsageSummary(Long)}。
     * </p>
     */
    public List<RedisKeyPrefixVO> listByDatasource(Long datasourceId) {
        return mapper.selectByDatasourceId(datasourceId).stream()
                .map(RedisKeyPrefixVO::fromEntity).collect(Collectors.toList());
    }

    /** 某数据源下全部前缀 + 使用计数（D11，管理页列表用，计数供删除按钮联动与「查看使用情况」入口） */
    public List<RedisKeyPrefixVO> listUsageSummary(Long datasourceId) {
        return mapper.selectByDatasourceId(datasourceId).stream().map(p -> {
            RedisKeyPrefixVO vo = RedisKeyPrefixVO.fromEntity(p);
            UsageCount c = countUsage(p.getId());
            vo.setScriptCount(c.scriptCount());
            vo.setFlowCount(c.flowCount());
            vo.setNodeCount(c.nodeCount());
            return vo;
        }).collect(Collectors.toList());
    }

    @Transactional
    public RedisKeyPrefixVO create(RedisKeyPrefixDTO dto) {
        validate(dto, null);
        RedisKeyPrefix e = new RedisKeyPrefix();
        applyDto(e, dto);
        e.setUid(UUID.randomUUID().toString());
        mapper.insert(e);
        log.info("新建Redis业务前缀: id={}, datasourceId={}, prefix={}", e.getId(), e.getDatasourceId(), e.getPrefix());
        return RedisKeyPrefixVO.fromEntity(e);
    }

    @Transactional
    public RedisKeyPrefixVO update(Long id, RedisKeyPrefixDTO dto) {
        RedisKeyPrefix e = mapper.selectById(id);
        AssertUtils.notNull(e, ErrorCode.REDIS_PREFIX_NOT_FOUND);
        validate(dto, id);
        String oldPrefix = e.getPrefix();
        applyDto(e, dto);
        mapper.update(e);
        if (!Objects.equals(oldPrefix, e.getPrefix())) {
            // 改前缀值等于换了一批 key：Redis 里旧 key 全部变孤儿，需重新生成（D8，前端已给影响面预览）
            log.warn("修改Redis业务前缀值: id={}, {} -> {}，原前缀下的缓存将变为孤儿数据", id, oldPrefix, e.getPrefix());
        }
        return RedisKeyPrefixVO.fromEntity(e);
    }

    /** 删除（D8 保护：被使用的前缀不允许删） */
    @Transactional
    public void delete(Long id) {
        AssertUtils.notNull(mapper.selectById(id), ErrorCode.REDIS_PREFIX_NOT_FOUND);
        UsageCount c = countUsage(id);
        // 有节点在实际使用 → 报影响面（比「被 N 个配置引用」更有说服力）；只有配置引用没节点用 → 报配置数
        if (c.nodeCount() > 0) {
            throw new BusinessException(ErrorCode.REDIS_PREFIX_IN_USE, c.flowCount(), c.nodeCount());
        }
        if (c.scriptCount() > 0) {
            throw new BusinessException(ErrorCode.REDIS_PREFIX_REF_BY_SCRIPT, c.scriptCount());
        }
        mapper.deleteById(id);
        log.info("删除Redis业务前缀: id={}", id);
    }

    // ==================== D11 使用统计 ====================

    /** 某前缀被哪些流程/节点使用（明细，用户点开才调，故可承担流程名查询） */
    public RedisPrefixUsageVO getUsage(Long prefixId) {
        RedisKeyPrefix p = mapper.selectById(prefixId);
        AssertUtils.notNull(p, ErrorCode.REDIS_PREFIX_NOT_FOUND);

        List<RedisScript> scripts = redisScriptMapper.selectByPrefixId(prefixId);
        var builder = RedisPrefixUsageVO.builder()
                .prefixId(p.getId()).prefix(p.getPrefix()).prefixName(p.getName())
                .datasourceId(p.getDatasourceId());
        if (scripts.isEmpty()) {
            return RedisPrefixUsageVO.empty(builder);
        }

        List<RedisPrefixUsageVO.Item> items = new ArrayList<>();
        Set<Long> flowIds = new HashSet<>();
        for (RedisScript s : scripts) {
            // 引用链第二跳：flow_node.config 里的 JSON 软引用（无外键无索引），复用既有引用追踪查询
            for (FlowNode n : flowNodeMapper.selectByConfigRef(NODE_TYPE_REDIS, CONFIG_KEY_REDIS_ID, String.valueOf(s.getId()))) {
                flowIds.add(n.getFlowId());
                items.add(RedisPrefixUsageVO.Item.builder()
                        .flowId(n.getFlowId()).nodeId(n.getId())
                        .nodeCode(n.getNodeCode()).nodeName(n.getNodeName())
                        .scriptId(s.getId()).scriptName(s.getName()).command(s.getCommand())
                        // 拼接规则直接复用执行期的同一静态实现，避免「统计看到的 key」与「实际落地的 key」两边漂移；
                        // 此处 redisKey 保留 ${} 原样，因为统计页拿不到运行时变量值
                        .fullKey(RedisScriptService.buildBusinessKey(p.getPrefix(), s.getRedisKey()))
                        .inline(s.getFlowId() != null)
                        .build());
            }
        }

        // 一次 IN 查询补流程名，避免逐条查
        Map<Long, String> flowNames = flowIds.isEmpty() ? Map.of()
                : flowMapper.selectByIds(flowIds).stream()
                        .collect(Collectors.toMap(Flow::getId, Flow::getName, (a, b) -> a));
        items.forEach(i -> i.setFlowName(flowNames.get(i.getFlowId())));
        items.sort(Comparator
                .comparing(RedisPrefixUsageVO.Item::getFlowId, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(RedisPrefixUsageVO.Item::getNodeId, Comparator.nullsLast(Comparator.naturalOrder())));

        return builder.scriptCount(scripts.size()).flowCount(flowIds.size()).nodeCount(items.size())
                .items(items).build();
    }

    /**
     * 轻量计数（不查流程名，供列表页与删除保护）。
     * <p>
     * scriptCount = 引用该前缀的配置数；flowCount = 有节点实际使用的流程数（按 flowId 去重）；
     * nodeCount = 实际使用的节点数（不去重）。内联配置即使没被任何节点引用也计入 scriptCount，
     * 但不计入 flowCount —— 「被使用」以节点实际引用为准。
     * </p>
     */
    private UsageCount countUsage(Long prefixId) {
        List<RedisScript> scripts = redisScriptMapper.selectByPrefixId(prefixId);
        if (scripts.isEmpty()) {
            return new UsageCount(0, 0, 0);
        }
        Set<Long> flowIds = new HashSet<>();
        int nodeCount = 0;
        for (RedisScript s : scripts) {
            List<FlowNode> nodes = flowNodeMapper.selectByConfigRef(NODE_TYPE_REDIS, CONFIG_KEY_REDIS_ID, String.valueOf(s.getId()));
            nodeCount += nodes.size();
            nodes.forEach(n -> flowIds.add(n.getFlowId()));
        }
        return new UsageCount(scripts.size(), flowIds.size(), nodeCount);
    }

    /** 使用计数三元组 */
    private record UsageCount(int scriptCount, int flowCount, int nodeCount) {}

    // ==================== 供其它服务调用 ====================

    /**
     * 取前缀值供 key 拼接。执行期实时查库（不加缓存），保证前缀改名后立即生效。
     *
     * @throws com.arbor.common.BusinessException 未选前缀或前缀已被删除
     */
    public String getPrefixValue(Long prefixId) {
        AssertUtils.notNull(prefixId, ErrorCode.REDIS_SCRIPT_NO_PREFIX);
        RedisKeyPrefix p = mapper.selectById(prefixId);
        AssertUtils.notNull(p, ErrorCode.REDIS_PREFIX_NOT_FOUND);
        return p.getPrefix();
    }

    /** 按 id 查前缀行（供 RedisScriptService 校验前缀与数据源是否匹配） */
    public RedisKeyPrefix getById(Long prefixId) {
        return prefixId != null ? mapper.selectById(prefixId) : null;
    }

    /** 批量取前缀值供 RedisScriptVO 回显完整 key（一次 IN 查询，避免 N+1） */
    public Map<Long, String> mapPrefixValues(Collection<Long> prefixIds) {
        List<Long> ids = prefixIds.stream().filter(Objects::nonNull).distinct().collect(Collectors.toList());
        if (ids.isEmpty()) {
            return Map.of();
        }
        return mapper.selectByIds(ids).stream()
                .collect(Collectors.toMap(RedisKeyPrefix::getId, RedisKeyPrefix::getPrefix, (a, b) -> a));
    }

    // ==================== 内部 ====================

    /**
     * 校验并规范化前缀：格式（D5 静态、无通配符）+ 数据源存在 + 同数据源下前缀值唯一（D1）。
     *
     * @param excludeId 修改时排除自身，新建传 null
     */
    private void validate(RedisKeyPrefixDTO dto, Long excludeId) {
        String prefix = dto.getPrefix() == null ? "" : dto.getPrefix().trim();
        AssertUtils.hasText(prefix, ErrorCode.REDIS_PREFIX_INVALID, "不能为空");
        AssertUtils.isTrue(prefix.length() <= PREFIX_MAX_LEN, ErrorCode.REDIS_PREFIX_INVALID,
                "长度不能超过 " + PREFIX_MAX_LEN + " 个字符");
        // 前缀用于层级管理与溯源，必须是静态值；带变量会让「某前缀被谁使用」失去意义
        AssertUtils.isFalse(prefix.contains("${"), ErrorCode.REDIS_PREFIX_INVALID,
                "不支持 ${} 变量，前缀必须是静态值");
        AssertUtils.isFalse(FORBIDDEN_PREFIX_CHARS.matcher(prefix).find(), ErrorCode.REDIS_PREFIX_INVALID,
                "不能包含空白字符或 * ? [ ] 通配符（会导致按模式清理缓存时误伤其它 key）");
        AssertUtils.isFalse(prefix.startsWith(":") || prefix.endsWith(":"), ErrorCode.REDIS_PREFIX_INVALID,
                "不能以冒号开头或结尾（分隔符由系统统一拼接）");
        dto.setPrefix(prefix);

        // 数据源必须存在；租户过滤保证只能选到自己的数据源
        AssertUtils.notNull(redisDatasourceMapper.selectById(dto.getDatasourceId()), ErrorCode.REDIS_DATASOURCE_NOT_FOUND);

        RedisKeyPrefix dup = mapper.selectByDatasourceIdAndPrefix(dto.getDatasourceId(), prefix);
        AssertUtils.isTrue(dup == null || dup.getId().equals(excludeId), ErrorCode.REDIS_PREFIX_DUPLICATE, prefix);
    }

    private void applyDto(RedisKeyPrefix e, RedisKeyPrefixDTO dto) {
        e.setDatasourceId(dto.getDatasourceId());
        e.setPrefix(dto.getPrefix());
        e.setName(dto.getName());
        e.setRemark(dto.getRemark());
    }
}
