package com.paddleocr.web.service.approval.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
import com.paddleocr.web.model.vo.TaskRecordVO;
import com.paddleocr.web.service.approval.InstanceStatus;
import com.paddleocr.web.service.approval.TaskStatus;
import com.paddleocr.web.service.approval.engine.FlowApproverResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 实例详情装配器：可见性判定 + 详情 VO 组装（审批时间线/当前用户视角）+ 待办人汇总
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
    private final ProcessVersionMapper processVersionMapper;
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
        vo.setDesignJson(version == null ? null : version.getDesignJson());
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
