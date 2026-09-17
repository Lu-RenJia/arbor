package com.arbor.engine;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 流程执行取消标记注册表（协作式取消，单实例内存实现）
 * <p>
 * 执行开始时 {@link #start} 注册，节点循环每轮执行前 {@link #isCancelled} 检查，
 * 用户点取消时由接口调用 {@link #requestCancel}，执行结束 {@link #finish} 清理。
 * <p>
 * key = userId:flowId，只能取消自己的执行。双时间戳比较（取消时间 >= 执行开始时间）
 * 保证"执行异常退出导致标记残留"或"取消请求晚于执行结束到达"时不会误杀下一次执行。
 * 将来若水平扩展多实例，仅需将本类内部实现替换为 Redis，对外接口不变。
 *
 * @author gwl
 */
@Component
public class FlowCancelRegistry {

    /** 正在执行标记：key -> 执行开始时间戳 */
    private final Map<String, Long> running = new ConcurrentHashMap<>();
    /** 取消请求标记：key -> 取消请求时间戳 */
    private final Map<String, Long> cancelReq = new ConcurrentHashMap<>();

    /** 执行开始时注册（节点循环前调用） */
    public void start(Long userId, Long flowId) {
        if (userId == null || flowId == null) return;
        running.put(key(userId, flowId), System.currentTimeMillis());
    }

    /** 用户取消时调用：仅当确有执行在跑时登记，否则直接忽略（避免残留标记误杀后续执行） */
    public void requestCancel(Long userId, Long flowId) {
        if (userId == null || flowId == null) return;
        if (running.containsKey(key(userId, flowId))) {
            cancelReq.put(key(userId, flowId), System.currentTimeMillis());
        }
    }

    /** 循环节点执行前检查：取消时间不早于本次执行开始时间才算有效 */
    public boolean isCancelled(Long userId, Long flowId) {
        if (userId == null || flowId == null) return false;
        String k = key(userId, flowId);
        Long cancelTs = cancelReq.get(k);
        Long startTs = running.get(k);
        return cancelTs != null && startTs != null && cancelTs >= startTs;
    }

    /** 执行结束清理（正常路径调用；异常路径靠双时间戳比较兜底，不会误杀） */
    public void finish(Long userId, Long flowId) {
        if (userId == null || flowId == null) return;
        String k = key(userId, flowId);
        running.remove(k);
        cancelReq.remove(k);
    }

    private static String key(Long userId, Long flowId) {
        return userId + ":" + flowId;
    }
}
