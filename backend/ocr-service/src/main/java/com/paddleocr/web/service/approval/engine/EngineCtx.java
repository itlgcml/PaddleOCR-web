package com.paddleocr.web.service.approval.engine;

import com.paddleocr.web.mapper.entity.FlowInstanceDO;
import com.paddleocr.web.mapper.entity.FlowTaskLogDO;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 引擎推进上下文：实例 + 设计快照游标 + 表单快照平面值（条件求值取值源）
 *
 * @author paddleocr
 * @date 2026-10-12
 */
@Getter
@RequiredArgsConstructor
public class EngineCtx {

    /** 审批实例 */
    private final FlowInstanceDO instance;

    /** 发起时版本快照游照（不可变） */
    private final FlowDesignCursor cursor;

    /** 表单快照平面值：fieldKey → 值（CONDITIONS 求值取值源） */
    private final Map<String, String> formValues;

    /** 指定审批人目标节点的一次性审批人覆盖（nodeId → userId）：人工路由/人工并行 PASS 携带
     * routeAssignees 登记（目标=列内首个审批节点，空列回退容器后继链首个审批节点，
     * routeAssigneeTarget 口径），建任务时消费并移除（覆盖节点默认解析，单人 OR 模式），
     * 未消费的残留随上下文丢弃 */
    private final Map<String, Long> assigneeOverrides = new HashMap<>();

    /** 本次操作待落库的审批记录：动作先暂存，推进完成后 flushActionLog 一次性落库——
     * 每次操作（发起/办理/撤销）仅生成一条 flow_task_log */
    @Setter
    private FlowTaskLogDO pendingActionLog;

    /** 推进过程中的引擎系统说明（降级/自动通过/汇合/退回等）：flush 时拼入操作记录 detail，
     * 不再单独成记录；条目自带【节点名】前缀区分发生位置 */
    private final List<String> engineNotes = new ArrayList<>();
}
