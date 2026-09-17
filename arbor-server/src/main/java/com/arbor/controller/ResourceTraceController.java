package com.arbor.controller;

import com.arbor.common.Result;
import com.arbor.service.ResourceTraceService;
import com.arbor.vo.ResourceTraceVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 引用追踪控制器：分析资源被哪些资源直接/间接引用
 *
 * @author gwl
 */
@RestController
@RequestMapping("/api/resource-trace")
@RequiredArgsConstructor
public class ResourceTraceController {

    private final ResourceTraceService traceService;

    /**
     * 追踪指定资源的引用链路
     *
     * @param type 资源类型: global_param / datasource / db_script / program_script /
     *             api_config / redis_datasource / redis_script / flow
     * @param id   资源ID
     */
    @GetMapping("/{type}/{id}")
    public Result<ResourceTraceVO> trace(@PathVariable String type, @PathVariable Long id) {
        return Result.ok(traceService.trace(type, id));
    }
}
