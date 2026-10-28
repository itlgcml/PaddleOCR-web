package com.paddleocr.web.service.approval.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.paddleocr.web.common.BusinessException;
import com.paddleocr.web.common.ResultCode;
import com.paddleocr.web.mapper.FlowCcMapper;
import com.paddleocr.web.mapper.FlowInstanceMapper;
import com.paddleocr.web.mapper.FlowTaskLogMapper;
import com.paddleocr.web.mapper.FlowTaskMapper;
import com.paddleocr.web.mapper.entity.FlowCcDO;
import com.paddleocr.web.mapper.entity.FlowInstanceDO;
import com.paddleocr.web.mapper.entity.FlowTaskDO;
import com.paddleocr.web.mapper.entity.FlowTaskLogDO;
import com.paddleocr.web.model.vo.InstanceDetailVO;
import com.paddleocr.web.model.vo.NodeProgressVO;
import com.paddleocr.web.model.vo.SubInstanceSummaryVO;
import com.paddleocr.web.model.vo.TaskRecordVO;
import com.paddleocr.web.service.approval.InstanceStatus;
import com.paddleocr.web.service.approval.NodeProgressStatus;
import com.paddleocr.web.service.approval.TaskStatus;
import com.paddleocr.web.service.approval.engine.FlowApproverResolver;
import com.paddleocr.web.service.approval.engine.FlowDesignCursor;
import com.paddleocr.web.service.approval.engine.FlowEngineSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.paddleocr.web.service.approval.engine.FlowDesignCursor.nameOf;

/**
 * 实例详情装配器：可见性判定 + 详情 VO 组装（审批时间线/当前用户视角）+ 待办人汇总
 *
 * <p>子流程（§8）：详情补充 parentInstanceId/subProcessName/subInstances（直接派生的子实例摘要）；
 * 子实例可见性并入所属流程树的参与人（根实例发起人/审批人/抄送人），父流程审批人可跟踪子流程进展。</p>
 *
 * @author paddleocr
 * @date 2026-10-12
 */
@Component
@RequiredArgsConstructor
public class InstanceDetailAssembler {

    private final FlowTaskMapper flowTaskMapper;
    private final FlowTaskLogMapper flowTaskLogMapper;
    private final FlowCcMapper flowCcMapper;
    private final FlowInstanceMapper flowInstanceMapper;
    private final FlowApproverResolver resolver;
    private final FlowEngineSupport support;

    /**
     * 装配实例详情（可见性不通过抛 40059）
     */
    public InstanceDetailVO assemble(FlowInstanceDO instance, Long userId) {
        if (!canView(instance, userId)) {
            throw new BusinessException(ResultCode.INSTANCE_VIEW_FORBIDDEN);
        }
        InstanceDetailVO vo = new InstanceDetailVO();
        vo.setId(String.valueOf(instance.getId()));
        vo.setProcessId(String.valueOf(instance.getProcessId()));
        vo.setProcessCode(instance.getProcessCode());
        vo.setProcessName(instance.getProcessName());
        vo.setProcessVersion(instance.getProcessVersion());
        vo.setBizType(instance.getBizType());
        vo.setBizId(String.valueOf(instance.getBizId()));
        vo.setBizTitle(instance.getBizTitle());
        vo.setStatus(instance.getStatus());
        vo.setStartUserId(String.valueOf(instance.getStartUserId()));
        vo.setStartUserName(instance.getStartUserName());
        vo.setStartTime(instance.getCreateTime());
        vo.setFinishTime(instance.getFinishTime());
        vo.setFormSnapshotJson(instance.getFormSnapshot());

        List<FlowTaskDO> tasks = flowTaskMapper.selectList(new LambdaQueryWrapper<FlowTaskDO>()
                .eq(FlowTaskDO::getInstanceId, instance.getId())
                .orderByDesc(FlowTaskDO::getCreateTime));
        // 设计快照按实例口径（§4.4.6）：子实例渲染自身 subDoc，根实例渲染版本快照
        String designJson = support.loadDesign(instance);
        vo.setDesignJson(designJson);
        vo.setParentInstanceId(instance.getParentInstanceId() == null
                ? null : String.valueOf(instance.getParentInstanceId()));
        // 父实例设计快照按父实例 ID 缓存：本实例名与子实例摘要共用一次解析
        Map<Long, FlowDesignCursor> parentCursorCache = new HashMap<>(2);
        vo.setSubProcessName(subProcessNameOf(instance, parentCursorCache));
        // 子实例列表一次查询：子流程摘要卡片与节点进度（SUB_PROCESS 状态映射）共用
        List<FlowInstanceDO> subInstances = listSubInstances(instance.getId());
        vo.setSubInstances(buildSubInstances(subInstances, parentCursorCache));
        vo.setRecords(buildRecords(instance.getId()));
        vo.setNodeProgress(buildNodeProgress(instance, designJson, tasks, subInstances));
        vo.setMyPendingTaskId(tasks.stream()
                .filter(task -> userId.equals(task.getAssigneeId()) && TaskStatus.PENDING.equals(task.getStatus()))
                .map(FlowTaskDO::getId)
                .findFirst()
                .map(String::valueOf)
                .orElse(null));
        // 子实例不可独立撤销（撤销须从根实例发起整树级联，§8）：避免前端显示撤销入口后收到 40068
        vo.setCanCancel(instance.getParentInstanceId() == null
                && userId.equals(instance.getStartUserId())
                && InstanceStatus.RUNNING.equals(instance.getStatus()));
        return vo;
    }

    /**
     * 可见性 = 发起人 ∪ 历史任务审批人 ∪ 抄送接收人 ∪ ADMIN（§8）；
     * 子实例额外并入所属流程树的参与人（树内实例的发起人/审批人/抄送人），父流程审批人可跟踪子流程进展
     */
    public boolean canView(FlowInstanceDO instance, Long userId) {
        if (userId == null) {
            return false;
        }
        if (userId.equals(instance.getStartUserId())) {
            return true;
        }
        Long taskCount = flowTaskMapper.selectCount(new LambdaQueryWrapper<FlowTaskDO>()
                .eq(FlowTaskDO::getInstanceId, instance.getId())
                .eq(FlowTaskDO::getAssigneeId, userId));
        if (taskCount != null && taskCount > 0) {
            return true;
        }
        Long ccCount = flowCcMapper.selectCount(new LambdaQueryWrapper<FlowCcDO>()
                .eq(FlowCcDO::getInstanceId, instance.getId())
                .eq(FlowCcDO::getReceiverId, userId));
        if (ccCount != null && ccCount > 0) {
            return true;
        }
        if (instance.getParentInstanceId() != null && isTreeParticipant(instance, userId)) {
            return true;
        }
        return resolver.listAdmins().stream().anyMatch(admin -> userId.equals(admin.getId()));
    }

    /**
     * 流程树参与人判定：用户是否为该实例所属流程树（根实例 + 全树子实例）的发起人/审批人/抄送人。
     * 沿 root_instance_id 批量取树内实例后统一判定，避免逐层回溯父实例（§8）
     */
    private boolean isTreeParticipant(FlowInstanceDO instance, Long userId) {
        List<Long> treeIds = loadTreeInstanceIds(instance);
        if (treeIds.isEmpty()) {
            return false;
        }
        Long startCount = flowInstanceMapper.selectCount(new LambdaQueryWrapper<FlowInstanceDO>()
                .in(FlowInstanceDO::getId, treeIds)
                .eq(FlowInstanceDO::getStartUserId, userId));
        if (startCount != null && startCount > 0) {
            return true;
        }
        Long taskCount = flowTaskMapper.selectCount(new LambdaQueryWrapper<FlowTaskDO>()
                .in(FlowTaskDO::getInstanceId, treeIds)
                .eq(FlowTaskDO::getAssigneeId, userId));
        if (taskCount != null && taskCount > 0) {
            return true;
        }
        Long ccCount = flowCcMapper.selectCount(new LambdaQueryWrapper<FlowCcDO>()
                .in(FlowCcDO::getInstanceId, treeIds)
                .eq(FlowCcDO::getReceiverId, userId));
        return ccCount != null && ccCount > 0;
    }

    /**
     * 树内全部实例 ID（根实例 + 全树子实例）：按 root_instance_id 命中；
     * root_instance_id 尚未回填的历史行（0/NULL）按 id 兜底命中根实例自身
     */
    private List<Long> loadTreeInstanceIds(FlowInstanceDO instance) {
        Long rootId = instance.getRootInstanceId();
        if (rootId == null || rootId.compareTo(0L) <= 0) {
            rootId = instance.getId();
        }
        final Long treeRootId = rootId;
        return flowInstanceMapper.selectList(new LambdaQueryWrapper<FlowInstanceDO>()
                        .and(wrapper -> wrapper.eq(FlowInstanceDO::getRootInstanceId, treeRootId)
                                .or()
                                .eq(FlowInstanceDO::getId, treeRootId)))
                .stream()
                .map(FlowInstanceDO::getId)
                .collect(Collectors.toList());
    }

    /**
     * 子流程节点名（实例为子实例时非空）：从父实例设计快照（父为根实例读版本表、父为子实例读冗余 subDoc）
     * 定位 parentNodeId 节点；父实例/节点缺失或快照不可解析时返回 null。
     * parentCursorCache 按父实例 ID 缓存游标，避免同一父实例的多个子实例重复解析快照
     */
    private String subProcessNameOf(FlowInstanceDO instance, Map<Long, FlowDesignCursor> parentCursorCache) {
        Long parentId = instance.getParentInstanceId();
        if (parentId == null || instance.getParentNodeId() == null) {
            return null;
        }
        if (!parentCursorCache.containsKey(parentId)) {
            FlowInstanceDO parent = flowInstanceMapper.selectById(parentId);
            parentCursorCache.put(parentId,
                    parent == null ? null : support.parseCursorQuietly(support.loadDesign(parent)));
        }
        FlowDesignCursor cursor = parentCursorCache.get(parentId);
        JsonNode node = cursor == null ? null : cursor.findNode(instance.getParentNodeId());
        return node == null ? null : nameOf(node);
    }

    /**
     * 本实例直接派生的子流程实例（按创建时间升序；无子实例时为空列表）
     */
    private List<FlowInstanceDO> listSubInstances(Long instanceId) {
        return flowInstanceMapper.selectList(new LambdaQueryWrapper<FlowInstanceDO>()
                .eq(FlowInstanceDO::getParentInstanceId, instanceId)
                .orderByAsc(FlowInstanceDO::getCreateTime));
    }

    /**
     * 子流程实例摘要组装（子实例列表与节点进度共用一次查询，按创建时间升序）
     */
    private List<SubInstanceSummaryVO> buildSubInstances(List<FlowInstanceDO> subs,
                                                         Map<Long, FlowDesignCursor> parentCursorCache) {
        List<SubInstanceSummaryVO> result = new ArrayList<>(subs.size());
        for (FlowInstanceDO sub : subs) {
            SubInstanceSummaryVO vo = new SubInstanceSummaryVO();
            vo.setId(String.valueOf(sub.getId()));
            vo.setName(sub.getProcessName());
            vo.setSubProcessName(subProcessNameOf(sub, parentCursorCache));
            vo.setAdvanceMode(sub.getAdvanceMode());
            vo.setStatus(sub.getStatus());
            vo.setCreateTime(sub.getCreateTime());
            vo.setFinishTime(sub.getFinishTime());
            result.add(vo);
        }
        return result;
    }

    // ==================== 节点进度（流程图页签） ====================

    /**
     * 节点进度组装：遍历设计快照先序全部节点，办理类节点（START/APPROVER/TRANSACT/SUB_PROCESS）
     * 按 flow_task 实时任务集判定——PENDING 任务=进行中、PASSED（含 AUTO_PASS 伪任务）=已走过、
     * 最新任务 REJECTED=已拒绝、无任务=未开始；START 发起即走过（退回发起人重建任务时由 PENDING
     * 规则覆盖为进行中）；TRANSACT 为引擎直通节点不建任务，恒按未开始近似（运行态不停留）；
     * SUB_PROCESS 按子实例状态映射（父实例在该节点无任务）。分支容器/分支列不下发，前端由列内
     * 节点状态聚合列视觉。快照不可解析时返回空列表（流程图页签降级为纯结构展示）
     */
    private List<NodeProgressVO> buildNodeProgress(FlowInstanceDO instance, String designJson,
                                                   List<FlowTaskDO> tasks, List<FlowInstanceDO> subInstances) {
        FlowDesignCursor cursor = support.parseCursorQuietly(designJson);
        if (cursor == null) {
            return Collections.emptyList();
        }
        Map<String, List<FlowTaskDO>> tasksByNode = new HashMap<>(tasks.size() * 2);
        for (FlowTaskDO task : tasks) {
            tasksByNode.computeIfAbsent(task.getNodeId(), key -> new ArrayList<>()).add(task);
        }
        // 子实例按父流程节点 ID 归位（列表 createTime 升序，覆盖取最新）
        Map<String, FlowInstanceDO> latestSubByNode = new HashMap<>(4);
        for (FlowInstanceDO sub : subInstances) {
            latestSubByNode.put(sub.getParentNodeId(), sub);
        }
        boolean running = InstanceStatus.RUNNING.equals(instance.getStatus());
        List<NodeProgressVO> result = new ArrayList<>();
        for (JsonNode node : cursor.preorder()) {
            String type = FlowDesignCursor.typeOf(node);
            if (isProgressNode(type)) {
                String nodeId = FlowDesignCursor.idOf(node);
                result.add(buildProgressOf(node, type, tasksByNode.get(nodeId), latestSubByNode.get(nodeId), running));
            }
        }
        return result;
    }

    /** 是否进度载体节点（分支容器/分支列不单独立状态） */
    private boolean isProgressNode(String type) {
        return FlowDesignCursor.TYPE_START.equals(type)
                || FlowDesignCursor.TYPE_APPROVER.equals(type)
                || FlowDesignCursor.TYPE_TRANSACT.equals(type)
                || FlowDesignCursor.TYPE_SUB_PROCESS.equals(type);
    }

    /**
     * 单节点进度判定：nodeTasks 为该节点全部任务（按 createTime 降序，组内首位即最新）。
     * 退回重走场景节点同时存在历史 PASSED 与新建 PENDING，PENDING 优先判进行中
     */
    private NodeProgressVO buildProgressOf(JsonNode node, String type, List<FlowTaskDO> nodeTasks,
                                           FlowInstanceDO subInstance, boolean running) {
        NodeProgressVO vo = new NodeProgressVO();
        vo.setNodeId(FlowDesignCursor.idOf(node));
        vo.setNodeName(FlowDesignCursor.nameOf(node));
        vo.setNodeType(type);
        if (FlowDesignCursor.TYPE_SUB_PROCESS.equals(type)) {
            fillSubProcessProgress(vo, subInstance);
            return vo;
        }
        if (nodeTasks == null || nodeTasks.isEmpty()) {
            // START 发起即走过（无任务口径）；其余无任务（含 TRANSACT 直通节点）视为未开始
            vo.setStatus(FlowDesignCursor.TYPE_START.equals(type)
                    ? NodeProgressStatus.PASSED : NodeProgressStatus.PENDING);
            vo.setAssigneeNames(Collections.emptyList());
            return vo;
        }
        boolean hasPending = false;
        LinkedHashSet<String> passedNames = new LinkedHashSet<>();
        LocalDateTime finishTime = null;
        for (FlowTaskDO task : nodeTasks) {
            if (TaskStatus.PENDING.equals(task.getStatus())) {
                hasPending = true;
            } else if (TaskStatus.PASSED.equals(task.getStatus())) {
                passedNames.add(task.getAssigneeName());
                if (finishTime == null) {
                    finishTime = task.getFinishTime();
                }
            }
        }
        if (hasPending && running) {
            vo.setStatus(NodeProgressStatus.CURRENT);
            vo.setAssigneeNames(nodeTasks.stream()
                    .filter(task -> TaskStatus.PENDING.equals(task.getStatus()))
                    .map(FlowTaskDO::getAssigneeName)
                    .distinct()
                    .collect(Collectors.toList()));
            return vo;
        }
        FlowTaskDO latest = nodeTasks.get(0);
        if (TaskStatus.REJECTED.equals(latest.getStatus())) {
            vo.setStatus(NodeProgressStatus.REJECTED);
            vo.setAssigneeNames(Collections.emptyList());
            vo.setFinishTime(latest.getFinishTime());
            return vo;
        }
        if (!passedNames.isEmpty()) {
            vo.setStatus(NodeProgressStatus.PASSED);
            vo.setAssigneeNames(new ArrayList<>(passedNames));
            vo.setFinishTime(finishTime);
            return vo;
        }
        // 仅剩被级联取消的任务（退回/拒绝时下游清场）：节点从未真实办理，视为未开始
        vo.setStatus(NodeProgressStatus.PENDING);
        vo.setAssigneeNames(Collections.emptyList());
        return vo;
    }

    /**
     * 子流程节点进度映射：父实例在 SUB_PROCESS 节点无任务（WAIT 等待/IMMEDIATE 旁路均由子实例承载），
     * 状态取最新子实例——RUNNING=进行中、COMPLETED=已走过、REJECTED=已拒绝、CANCELED=已取消
     * （退回重走时旧子实例取消、新子实例运行，取最新天然正确）；无子实例=未开始。
     * 附子实例 ID 供前端跳转子实例详情
     */
    private void fillSubProcessProgress(NodeProgressVO vo, FlowInstanceDO subInstance) {
        vo.setAssigneeNames(Collections.emptyList());
        if (subInstance == null) {
            vo.setStatus(NodeProgressStatus.PENDING);
            return;
        }
        switch (subInstance.getStatus()) {
            case InstanceStatus.RUNNING:
                vo.setStatus(NodeProgressStatus.CURRENT);
                break;
            case InstanceStatus.COMPLETED:
                vo.setStatus(NodeProgressStatus.PASSED);
                break;
            case InstanceStatus.REJECTED:
                vo.setStatus(NodeProgressStatus.REJECTED);
                break;
            case InstanceStatus.CANCELED:
                vo.setStatus(NodeProgressStatus.CANCELED);
                break;
            default:
                vo.setStatus(NodeProgressStatus.PENDING);
                break;
        }
        vo.setFinishTime(subInstance.getFinishTime());
        vo.setSubInstanceId(String.valueOf(subInstance.getId()));
    }

    /**
     * 批量汇总实例当前待办人姓名（PENDING 根任务 assigneeName 去重保序；列表徽标/我发起的列表共用）
     */
    public Map<Long, List<String>> currentApproverNames(Collection<Long> instanceIds) {
        if (instanceIds == null || instanceIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<FlowTaskDO> tasks = flowTaskMapper.selectList(new LambdaQueryWrapper<FlowTaskDO>()
                .in(FlowTaskDO::getInstanceId, instanceIds)
                .eq(FlowTaskDO::getStatus, TaskStatus.PENDING)
                .isNull(FlowTaskDO::getParentTaskId));
        Map<Long, LinkedHashSet<String>> grouped = new LinkedHashMap<>();
        for (FlowTaskDO task : tasks) {
            grouped.computeIfAbsent(task.getInstanceId(), key -> new LinkedHashSet<>())
                    .add(task.getAssigneeName());
        }
        Map<Long, List<String>> result = new LinkedHashMap<>();
        grouped.forEach((instanceId, names) -> result.put(instanceId, new ArrayList<>(names)));
        return result;
    }

    // ==================== 内部组装 ====================

    private List<TaskRecordVO> buildRecords(Long instanceId) {
        List<FlowTaskLogDO> logs = flowTaskLogMapper.selectList(new LambdaQueryWrapper<FlowTaskLogDO>()
                .eq(FlowTaskLogDO::getInstanceId, instanceId)
                .orderByAsc(FlowTaskLogDO::getCreateTime)
                .orderByAsc(FlowTaskLogDO::getId));
        return logs.stream().map(logRecord -> {
            TaskRecordVO vo = new TaskRecordVO();
            vo.setId(String.valueOf(logRecord.getId()));
            vo.setAction(logRecord.getAction());
            vo.setNodeName(logRecord.getNodeName());
            vo.setOperatorName(logRecord.getOperatorName());
            vo.setComment(logRecord.getComment());
            vo.setDetail(logRecord.getDetail());
            vo.setCreateTime(logRecord.getCreateTime());
            return vo;
        }).collect(Collectors.toList());
    }
}
