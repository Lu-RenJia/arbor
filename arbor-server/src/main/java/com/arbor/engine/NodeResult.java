package com.arbor.engine;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 节点执行结果
 *
 * @author gwl
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NodeResult {

    /** 节点状态 - 成功 */
    public static final String STATUS_SUCCESS = "SUCCESS";
    /** 节点状态 - 失败 */
    public static final String STATUS_FAIL = "FAIL";
    /** 节点状态 - 流程停止（节点未执行，因前置节点触发停止条件） */
    public static final String STATUS_STOPPED = "STOPPED";
    /** 流程状态 - 已取消（用户主动取消执行） */
    public static final String STATUS_CANCELLED = "CANCELLED";

    /** 节点编码 */
    private String nodeCode;

    /** 节点名称 */
    private String nodeName;

    /** 节点类型 */
    private String nodeType;

    /** 是否执行成功 */
    private boolean success;

    /** 节点状态：SUCCESS/FAIL/STOPPED（STOPPED 表示因流程停止未执行） */
    private String status;

    /** 结果数据 */
    private Object data;

    /** 错误信息 */
    private String errorMessage;

    /** 节点执行耗时（毫秒） */
    private Long duration;

    /** 子流程内部节点执行明细（仅 REF_FLOW 节点有值，不参与下游参数解析，仅供日志展示） */
    private List<Map<String, Object>> subFlowNodeDetails;

    public static NodeResult ok(Object data) {
        return NodeResult.builder().success(true).status(STATUS_SUCCESS).data(data).build();
    }

    public static NodeResult fail(String errorMessage) {
        return NodeResult.builder().success(false).status(STATUS_FAIL).errorMessage(errorMessage).build();
    }

    /**
     * 创建结构化的节点结果（含节点信息和耗时）
     *
     * @param nodeCode  节点编码
     * @param nodeName  节点名称
     * @param nodeType  节点类型
     * @param success   是否成功
     * @param data      结果数据
     * @param errorMsg  错误信息
     * @param duration  耗时（毫秒）
     * @return NodeResult
     */
    public static NodeResult of(String nodeCode, String nodeName, String nodeType,
                                 boolean success, Object data, String errorMsg, long duration) {
        return NodeResult.builder()
                .nodeCode(nodeCode)
                .nodeName(nodeName)
                .nodeType(nodeType)
                .success(success)
                .status(success ? STATUS_SUCCESS : STATUS_FAIL)
                .data(data)
                .errorMessage(errorMsg)
                .duration(duration)
                .build();
    }

    /**
     * 创建「流程停止」占位结果（前置节点触发停止条件，本节点未执行）
     *
     * @param nodeCode 节点编码
     * @param nodeName 节点名称
     * @param nodeType 节点类型
     * @return NodeResult（status=STOPPED）
     */
    public static NodeResult stopped(String nodeCode, String nodeName, String nodeType) {
        return NodeResult.builder()
                .nodeCode(nodeCode)
                .nodeName(nodeName)
                .nodeType(nodeType)
                .success(false)
                .status(STATUS_STOPPED)
                .duration(0L)
                .build();
    }
}
