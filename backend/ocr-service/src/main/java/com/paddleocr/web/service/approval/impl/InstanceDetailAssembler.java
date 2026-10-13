package com.paddleocr.web.service.approval.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paddleocr.web.common.BusinessException;
import com.paddleocr.web.common.ResultCode;
import com.paddleocr.web.mapper.FlowCcMapper;
import com.paddleocr.web.mapper.FlowTaskLogMapper;
import com.paddleocr.web.mapper.FlowTaskMapper;
import com.paddleocr.web.mapper.ProcessVersionMapper;
import com.paddleocr.web.mapper.entity.FlowCcDO;
import com.paddleocr.web.mapper.entity.FlowInstanceDO;
import com.paddleocr.web.mapper.entity.FlowTaskDO;
import com.paddleocr.web.mapper.entity.FlowTaskLogDO;
import com.paddleocr.web.mapper.entity.ProcessVersionDO;
import com.paddleocr.web.model.vo.InstanceDetailVO;
import com.paddleocr.web.model.vo.NodeProgressVO;
import com.paddleocr.web.model.vo.TaskRecordVO;
import com.paddleocr.web.service.approval.InstanceStatus;
import com.paddleocr.web.service.approval.TaskStatus;
import com.paddleocr.web.service.approval.engine.FlowApproverResolver;
import com.paddleocr.web.service.approval.engine.FlowDesignCursor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static com.paddleocr.web.service.approval.engine.FlowDesignCursor.TYPE_CONDITIONS;
import static com.paddleocr.web.service.approval.engine.FlowDesignCursor.branchColumns;
import static com.paddleocr.web.service.approval.engine.FlowDesignCursor.idOf;
import static com.paddleocr.web.service.approval.engine.FlowDesignCursor.nameOf;
import static com.paddleocr.web.service.approval.engine.FlowDesignCursor.typeOf;

/**
 * 实例详情装配器：可见性判定 + 详情 VO 组装（节点进度/审批时间线/当前用户视角）+ 待办人汇总
 *
 * <p>节点进度推导（§9.4）：有 PENDING 任务（含派生）→ CURRENT；有 REJECTED → REJECTED；
 * 有 PASSED（含 AUTO_PASS 伪任务）→ PASSED；无任务的未到达节点 → PENDING，
 * CONDITIONS 容器已路由但分支无痕迹的无任务节点 → SKIPPED（PARALLEL 全分支激活不标记）。</p>
 *
 * @author paddleocr
 * @date 2026-10-12
 */
@Component
@RequiredArgsConstructor
public class InstanceDetailAssembler {

    private static final String PROGRESS_CURRENT = "CURRENT";
    private static final String PROGRESS_PENDING = "PENDING";
    private static final String PROGRESS_PASSED = "PASSED";
    private static final String PROGRESS_REJECTED = "REJECTED";
    private static final String PROGRESS_SKIPPED = "SKIPPED";

    private final FlowTaskMapper flowTaskMapper;
    private final FlowTaskLogMapper flowTaskLogMapper;
    private final FlowCcMapper flowCcMapper;
    private final ProcessVersionMapper processVersionMapper;
    private final ObjectMapper objectMapper;
    private final FlowApproverResolver resolver;

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
        ProcessVersionDO version = processVersionMapper.selectById(instance.getProcessVersionId());
        String designJson = version == null ? null : version.getDesignJson();
        vo.setDesignJson(designJson);
        vo.setNodes(buildNodes(parseQuietly(designJson), groupByNode(tasks)));
        vo.setRecords(buildRecords(instance.getId()));
        vo.setMyPendingTaskId(tasks.stream()
                .filter(task -> userId.equals(task.getAssigneeId()) && TaskStatus.PENDING.equals(task.getStatus()))
                .map(FlowTaskDO::getId)
                .findFirst()
                .map(String::valueOf)
                .orElse(null));
        vo.setCanCancel(userId.equals(instance.getStartUserId())
                && InstanceStatus.RUNNING.equals(instance.getStatus()));
        return vo;
    }

    /**
     * 可见性 = 发起人 ∪ 历史任务审批人 ∪ 抄送接收人 ∪ ADMIN（§8）
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
        return resolver.listAdmins().stream().anyMatch(admin -> userId.equals(admin.getId()));
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

    /**
     * 快照解析（展示降级口径）：解析失败返回 null，节点进度置空不影响其余详情
     */
    private FlowDesignCursor parseQuietly(String designJson) {
        if (designJson == null || designJson.isEmpty()) {
            return null;
        }
        try {
            return FlowDesignCursor.of(objectMapper.readTree(designJson));
        } catch (Exception e) {
            return null;
        }
    }

    private Map<String, List<FlowTaskDO>> groupByNode(List<FlowTaskDO> tasks) {
        return tasks.stream().collect(Collectors.groupingBy(
                FlowTaskDO::getNodeId, LinkedHashMap::new, Collectors.toList()));
    }

    private List<NodeProgressVO> buildNodes(FlowDesignCursor cursor, Map<String, List<FlowTaskDO>> tasksByNode) {
        if (cursor == null) {
            return Collections.emptyList();
        }
        List<NodeProgressVO> nodes = new ArrayList<>();
        Map<String, NodeProgressVO> byNodeId = new LinkedHashMap<>();
        for (JsonNode node : cursor.preorder()) {
            String nodeId = idOf(node);
            List<FlowTaskDO> tasks = tasksByNode.getOrDefault(nodeId, Collections.emptyList());
            NodeProgressVO vo = new NodeProgressVO();
            vo.setNodeId(nodeId);
            vo.setNodeName(nameOf(node));
            vo.setNodeType(typeOf(node));
            vo.setStatus(progressStatus(tasks));
            vo.setAssigneeNames(tasks.stream()
                    .map(FlowTaskDO::getAssigneeName)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toCollection(LinkedHashSet::new))
                    .stream().collect(Collectors.toList()));
            vo.setFinishTime(tasks.stream()
                    .filter(task -> TaskStatus.PASSED.equals(task.getStatus()))
                    .map(FlowTaskDO::getFinishTime)
                    .filter(Objects::nonNull)
                    .reduce((first, second) -> second)
                    .orElse(null));
            nodes.add(vo);
            byNodeId.put(nodeId, vo);
        }
        markSkippedBranches(cursor, tasksByNode, byNodeId);
        return nodes;
    }

    /**
     * 进度状态：PENDING 任务优先（会签进行中= CURRENT）→ REJECTED → PASSED → 未到达 PENDING
     */
    private String progressStatus(List<FlowTaskDO> tasks) {
        boolean hasPending = false;
        boolean hasRejected = false;
        boolean hasPassed = false;
        for (FlowTaskDO task : tasks) {
            if (TaskStatus.PENDING.equals(task.getStatus())) {
                hasPending = true;
            } else if (TaskStatus.REJECTED.equals(task.getStatus())) {
                hasRejected = true;
            } else if (TaskStatus.PASSED.equals(task.getStatus())) {
                hasPassed = true;
            }
        }
        if (hasPending) {
            return PROGRESS_CURRENT;
        }
        if (hasRejected) {
            return PROGRESS_REJECTED;
        }
        if (hasPassed) {
            return PROGRESS_PASSED;
        }
        return PROGRESS_PENDING;
    }

    /**
     * CONDITIONS 容器已路由（任一分支有任务痕迹）时，未命中分支的无任务节点标记 SKIPPED
     */
    private void markSkippedBranches(FlowDesignCursor cursor, Map<String, List<FlowTaskDO>> tasksByNode,
                                     Map<String, NodeProgressVO> byNodeId) {
        for (JsonNode node : cursor.preorder()) {
            if (!TYPE_CONDITIONS.equals(typeOf(node))) {
                continue;
            }
            List<JsonNode> untouchedColumns = new ArrayList<>();
            boolean anyTouched = false;
            for (JsonNode column : branchColumns(node)) {
                boolean touched = cursor.collectBranchNodeIds(column).stream()
                        .anyMatch(tasksByNode::containsKey);
                if (touched) {
                    anyTouched = true;
                } else {
                    untouchedColumns.add(column);
                }
            }
            if (!anyTouched) {
                continue;
            }
            for (JsonNode column : untouchedColumns) {
                for (String nodeId : cursor.collectBranchNodeIds(column)) {
                    NodeProgressVO vo = byNodeId.get(nodeId);
                    if (vo != null && PROGRESS_PENDING.equals(vo.getStatus())) {
                        vo.setStatus(PROGRESS_SKIPPED);
                    }
                }
            }
        }
    }

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
