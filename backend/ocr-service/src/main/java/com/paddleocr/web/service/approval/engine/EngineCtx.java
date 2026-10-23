package com.paddleocr.web.service.approval.engine;

import com.paddleocr.web.mapper.entity.FlowInstanceDO;
import com.paddleocr.web.mapper.entity.FlowTaskLogDO;
import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 引擎推进上下文：实例 + 设计快照游标 + 表单快照平面值（条件求值取值源）+ 流程树身份
 *
 * <p>树身份（rootInstanceId/depth/parentInstanceId）从实例行派生：根实例=自身/0/null，
 * 子实例=继承父实例；推进锁按 rootInstanceId 取树级锁，子流程嵌套与级联终止按 depth/parentInstanceId 判定。</p>
 *
 * @author paddleocr
 * @date 2026-10-12
 */
@Getter
public class EngineCtx {

    /** 审批实例 */
    private final FlowInstanceDO instance;

    /** 发起时版本快照游照（不可变） */
    private final FlowDesignCursor cursor;

    /** 表单快照平面值：fieldKey → 值（CONDITIONS 求值取值源） */
    private final Map<String, String> formValues;

    /** 流程树根实例 ID（根实例=自身；子实例=父实例的 rootInstanceId；旧数据缺省回退自身） */
    private final Long rootInstanceId;

    /** 嵌套深度（根实例=0；子实例=父实例 depth + 1） */
    private final Integer depth;

    /** 父实例 ID（根实例为 null；子实例指向创建它的父实例） */
    private final Long parentInstanceId;

    /** 指定审批人目标节点的一次性审批人覆盖（nodeId → userIds，支持或签单选/会签多选）：
     * 发起 startAssignees（第一审批阶段节点，含 IMMEDIATE 子流程主/子双线与 WAIT 穿透子 doc 首组）、
     * 人工路由/人工并行 PASS 携带 routeAssignees（目标=列内首个审批节点，空列回退容器后继链首个
     * 审批节点，routeAssigneeTarget 口径）、子流程 PASS 携带 subProcessAssignees 登记；
     * 建任务时消费并移除（覆盖节点默认解析，按节点 multiMode 建任务），
     * 未消费的残留随上下文丢弃；子流程节点覆盖在 buildSubInstance 时复制到子上下文消费 */
    private final Map<String, List<Long>> assigneeOverrides = new HashMap<>();

    /** 本次操作待落库的审批记录：动作先暂存，推进完成后 flushActionLog 一次性落库——
     * 每次操作（发起/办理/撤销）仅生成一条 flow_task_log */
    @Setter
    private FlowTaskLogDO pendingActionLog;

    /** 推进过程中的引擎系统说明（降级/自动通过/汇合/退回/子流程等）：flush 时拼入操作记录 detail，
     * 不再单独成记录；条目自带【节点名】前缀区分发生位置 */
    private final List<String> engineNotes = new ArrayList<>();

    public EngineCtx(FlowInstanceDO instance, FlowDesignCursor cursor, Map<String, String> formValues) {
        this.instance = instance;
        this.cursor = cursor;
        this.formValues = formValues;
        Long root = instance.getRootInstanceId();
        this.rootInstanceId = root == null || root.compareTo(0L) <= 0 ? instance.getId() : root;
        Integer instanceDepth = instance.getDepth();
        this.depth = instanceDepth == null ? Integer.valueOf(0) : instanceDepth;
        this.parentInstanceId = instance.getParentInstanceId();
    }
}
