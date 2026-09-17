package com.arbor.controller;

import com.arbor.common.AssertUtils;
import com.arbor.common.EnvironmentContext;
import com.arbor.common.ErrorCode;
import com.arbor.common.PageResult;
import com.arbor.common.Result;
import com.arbor.dto.FlowSaveDTO;
import com.arbor.engine.FlowExecutionResult;
import com.arbor.service.EnvironmentService;
import com.arbor.service.FlowDtoExecutionService;
import com.arbor.service.FlowService;
import com.arbor.vo.FlowVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 流程管理控制器
 *
 * @author gwl
 */
@Slf4j
@RestController
@RequestMapping("/api/flow")
@RequiredArgsConstructor
public class FlowController {

    private final FlowService flowService;
    private final FlowDtoExecutionService flowDtoExecutionService;
    private final EnvironmentService environmentService;
    private final com.arbor.engine.FlowCancelRegistry cancelRegistry;

    /** 分页查询（支持名称模糊、节点数过滤、排序） */
    @GetMapping("/page")
    public Result<PageResult<FlowVO>> page(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) Integer nodeCount,
            @RequestParam(required = false) String sortField,
            @RequestParam(required = false) String sortOrder) {
        return Result.ok(flowService.page(pageNum, pageSize, name, nodeCount, sortField, sortOrder));
    }

    /** 流程详情（含节点列表） */
    @GetMapping("/{id}")
    public Result<FlowVO> detail(@PathVariable Long id) {
        return Result.ok(flowService.getDetail(id));
    }

    /** 新增流程 */
    @PostMapping
    public Result<FlowVO> create(@Valid @RequestBody FlowSaveDTO dto) {
        return Result.ok("添加成功", flowService.create(dto));
    }

    /** 修改流程基本信息 */
    @PutMapping("/{id}")
    public Result<FlowVO> update(@PathVariable Long id, @Valid @RequestBody FlowSaveDTO dto) {
        return Result.ok("修改成功", flowService.update(id, dto));
    }

    /** 删除流程 */
    @DeleteMapping("/{id}")
    public Result<String> delete(@PathVariable Long id) {
        flowService.delete(id);
        return Result.ok("删除成功");
    }

    /** 启用/关闭流程 */
    @PutMapping("/status/{id}")
    public Result<String> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        flowService.updateStatus(id, status);
        return Result.ok(status == 1 ? "已启用" : "已关闭");
    }

    /** 保存流程节点配置 */
    @PutMapping("/nodes/{id}")
    public Result<String> saveNodes(@PathVariable Long id, @RequestBody FlowSaveDTO dto) {
        flowService.saveNodes(id, dto.getNodes());
        return Result.ok("保存成功");
    }

    /** 获取流程节点列表 */
    @GetMapping("/nodes/{id}")
    public Result<List<FlowVO.FlowNodeVO>> getNodes(@PathVariable Long id) {
        return Result.ok(flowService.getNodes(id));
    }

    /** 保存流程节点示例输出 */
    @PutMapping("/examples/{id}")
    public Result<String> saveExamples(@PathVariable Long id, @RequestBody List<FlowVO.FlowNodeVO> nodes) {
        flowService.saveExamples(id, nodes);
        return Result.ok("保存成功");
    }

    /** 手动执行流程（可选环境参数，传入编辑中的节点列表时不持久化） */
    @PostMapping("/execute/{id}")
    public Result<FlowExecutionResult> execute(
            @PathVariable Long id,
            @RequestBody(required = false) FlowSaveDTO dto,
            @RequestParam(required = false) Long envId) {
        // 解析执行环境：优先请求参数，否则用户默认环境
        Long effectiveEnvId = envId != null ? envId : environmentService.getDefaultEnvId();
        var env = environmentService.getByIdCached(effectiveEnvId);
        AssertUtils.notNull(env, ErrorCode.ENV_NOT_FOUND);
        // 设置环境上下文，流程内全局参数/数据源自动按环境取值
        EnvironmentContext.set(effectiveEnvId, env.getCode());
        try {
            if (dto != null && dto.getNodes() != null && !dto.getNodes().isEmpty()) {
                return Result.ok(flowDtoExecutionService.executeWithNodes(id, dto.getNodes()));
            }
            return Result.ok(flowService.manualExecute(id));
        } finally {
            EnvironmentContext.clear();
        }
    }

    /** 取消当前用户正在执行的流程（在下一个节点执行前生效，不打断正在执行的当前节点） */
    @PostMapping("/cancel/{id}")
    public Result<String> cancel(@PathVariable Long id) {
        cancelRegistry.requestCancel(com.arbor.common.TenantContext.getUserId(), id);
        return Result.ok("已发送取消请求");
    }
}
