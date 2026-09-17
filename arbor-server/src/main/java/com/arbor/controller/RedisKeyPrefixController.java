package com.arbor.controller;

import com.arbor.common.Result;
import com.arbor.dto.RedisKeyPrefixDTO;
import com.arbor.service.RedisKeyPrefixService;
import com.arbor.vo.RedisKeyPrefixVO;
import com.arbor.vo.RedisPrefixUsageVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Redis 业务前缀管理（D1/D8/D11）
 * <p>
 * 前缀挂在 Redis 数据源下，一个数据源可登记多个前缀；Redis 配置必须选其中一个，
 * 最终 key = {@code {prefix}:{用户填的 redisKey}}，不再由系统追加 env/user/flow 前缀。
 * </p>
 * <p>
 * 与 {@code RedisScriptController} 等资源类 Controller 一致，不加 {@code @RequiresPermission}：
 * 靠登录态 + 租户过滤（{@code redis_key_prefix} 不在 {@code SHARED_TABLES} 白名单，
 * 全员隔离，管理员也只看自己登记的前缀）保证数据边界。
 * </p>
 *
 * @author gwl
 */
@RestController
@RequestMapping("/api/redis-key-prefix")
@RequiredArgsConstructor
public class RedisKeyPrefixController {

    private final RedisKeyPrefixService service;

    /** 某数据源下的前缀列表（轻量，不带计数）—— Redis 配置弹窗的前缀下拉用 */
    @GetMapping("/datasource/{datasourceId}")
    public Result<List<RedisKeyPrefixVO>> listByDatasource(@PathVariable Long datasourceId) {
        return Result.ok(service.listByDatasource(datasourceId));
    }

    /** 某数据源下的前缀列表 + 使用计数 —— 数据源页的前缀管理表格用（D11） */
    @GetMapping("/datasource/{datasourceId}/usage")
    public Result<List<RedisKeyPrefixVO>> usageSummary(@PathVariable Long datasourceId) {
        return Result.ok(service.listUsageSummary(datasourceId));
    }

    /** 某前缀被哪些流程/节点使用的明细（D11，点开才查） */
    @GetMapping("/{id}/usage")
    public Result<RedisPrefixUsageVO> usage(@PathVariable Long id) {
        return Result.ok(service.getUsage(id));
    }

    @PostMapping
    public Result<RedisKeyPrefixVO> create(@Valid @RequestBody RedisKeyPrefixDTO dto) {
        return Result.ok("添加成功", service.create(dto));
    }

    @PutMapping("/{id}")
    public Result<RedisKeyPrefixVO> update(@PathVariable Long id, @Valid @RequestBody RedisKeyPrefixDTO dto) {
        return Result.ok("修改成功", service.update(id, dto));
    }

    /** 删除（被使用的前缀会被 Service 拦下并返回影响面，D8） */
    @DeleteMapping("/{id}")
    public Result<String> delete(@PathVariable Long id) {
        service.delete(id);
        return Result.ok("删除成功");
    }
}
