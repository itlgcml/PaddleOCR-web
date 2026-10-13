package com.paddleocr.web.service.approval.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.paddleocr.web.mapper.FlowInstanceMapper;
import com.paddleocr.web.mapper.FlowTaskMapper;
import com.paddleocr.web.mapper.entity.FlowInstanceDO;
import com.paddleocr.web.mapper.entity.FlowTaskDO;
import com.paddleocr.web.mapper.entity.UserDO;
import com.paddleocr.web.model.PageResultVO;
import com.paddleocr.web.model.dto.RuntimePageQuery;
import com.paddleocr.web.model.dto.TaskActionDTO;
import com.paddleocr.web.model.vo.InstanceDetailVO;
import com.paddleocr.web.model.vo.ReturnTargetVO;
import com.paddleocr.web.model.vo.TaskDetailVO;
import com.paddleocr.web.model.vo.TaskVO;
import com.paddleocr.web.service.approval.FlowInstanceService;
import com.paddleocr.web.service.approval.FlowTaskService;
import com.paddleocr.web.service.approval.TaskStatus;
import com.paddleocr.web.service.approval.engine.FlowDesignCursor;
import com.paddleocr.web.service.approval.engine.FlowEngine;
import com.paddleocr.web.service.approval.engine.FlowEngineSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.paddleocr.web.service.approval.engine.FlowDesignCursor.TYPE_APPROVER;
import static com.paddleocr.web.service.approval.engine.FlowDesignCursor.idOf;
import static com.paddleocr.web.service.approval.engine.FlowDesignCursor.nameOf;
import static com.paddleocr.web.service.approval.engine.FlowDesignCursor.typeOf;

/**
 * 审批任务服务实现
 *
 * @author paddleocr
 * @date 2026-10-12
 */
@Service
@RequiredArgsConstructor
public class FlowTaskServiceImpl implements FlowTaskService {

    private static final String ACTION_RETURN = "RETURN";

    private final FlowTaskMapper flowTaskMapper;
    private final FlowInstanceMapper flowInstanceMapper;
    private final FlowEngineSupport support;
    private final FlowEngine engine;
    private final FlowInstanceService flowInstanceService;

    @Override
    public PageResultVO<TaskVO> pageTodo(RuntimePageQuery query, Long userId) {
        LambdaQueryWrapper<FlowTaskDO> wrapper = new LambdaQueryWrapper<FlowTaskDO>()
                .eq(FlowTaskDO::getAssigneeId, userId)
                .eq(FlowTaskDO::getStatus, TaskStatus.PENDING)
                .orderByDesc(FlowTaskDO::getCreateTime);
        return toTaskPage(selectTaskPage(query, wrapper));
    }

    @Override
    public PageResultVO<TaskVO> pageDone(RuntimePageQuery query, Long userId) {
        boolean hasAction = query.getAction() != null && !query.getAction().trim().isEmpty();
        LambdaQueryWrapper<FlowTaskDO> wrapper = new LambdaQueryWrapper<FlowTaskDO>()
                .eq(FlowTaskDO::getAssigneeId, userId)
                .in(FlowTaskDO::getStatus, Arrays.asList(
                        TaskStatus.PASSED, TaskStatus.REJECTED, TaskStatus.TRANSFERRED))
                .eq(hasAction, FlowTaskDO::getAction, hasAction ? query.getAction().trim() : null)
                .orderByDesc(FlowTaskDO::getFinishTime);
        return toTaskPage(selectTaskPage(query, wrapper));
    }

    @Override
    public TaskDetailVO detail(Long taskId, Long userId) {
        FlowTaskDO task = support.requireTask(taskId);
        // 实例详情组装内含参与人可见性校验（非参与人 40059）
        InstanceDetailVO base = flowInstanceService.detail(task.getInstanceId(), userId);

        TaskDetailVO vo = new TaskDetailVO();
        BeanUtils.copyProperties(base, vo);
        FlowInstanceDO instance = flowInstanceMapper.selectById(task.getInstanceId());
        vo.setTask(toTaskVO(task, instance));

        FlowDesignCursor cursor = support.parseCursorQuietly(
                instance == null ? null : support.loadVersionDesignJson(instance.getProcessVersionId()));
        vo.setButtons(Collections.emptyList());
        vo.setReturnTargets(Collections.emptyList());
        if (cursor != null) {
            JsonNode node = cursor.findNode(task.getNodeId());
            vo.setButtons(support.readButtons(node));
            vo.setReturnTargets(buildReturnTargets(cursor, node, task));
        }
        vo.setActionable(userId.equals(task.getAssigneeId())
                && TaskStatus.PENDING.equals(task.getStatus()));
        return vo;
    }

    @Override
    public void action(Long taskId, TaskActionDTO dto, Long userId) {
        UserDO operator = support.requireUserById(userId);
        engine.handleAction(taskId, dto, operator);
    }

    // ==================== 内部工具 ====================

    private Page<FlowTaskDO> selectTaskPage(RuntimePageQuery query, LambdaQueryWrapper<FlowTaskDO> wrapper) {
        int pageNum = query.getPageNum() == null || query.getPageNum() < 1 ? 1 : query.getPageNum();
        int pageSize = query.getPageSize() == null || query.getPageSize() < 1 ? 10
                : Math.min(query.getPageSize(), 100);
        List<Long> keywordInstanceIds = keywordInstanceIds(query.getKeyword());
        if (keywordInstanceIds != null && keywordInstanceIds.isEmpty()) {
            return new Page<>(pageNum, pageSize);
        }
        wrapper.in(keywordInstanceIds != null, FlowTaskDO::getInstanceId, keywordInstanceIds);
        return flowTaskMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
    }

    private PageResultVO<TaskVO> toTaskPage(Page<FlowTaskDO> page) {
        List<FlowTaskDO> records = page.getRecords();
        Map<Long, FlowInstanceDO> instanceMap = loadInstances(records);
        List<TaskVO> vos = records.stream()
                .map(task -> toTaskVO(task, instanceMap.get(task.getInstanceId())))
                .collect(Collectors.toList());
        PageResultVO<TaskVO> result = new PageResultVO<>();
        result.setRecords(vos);
        result.setTotal(page.getTotal());
        result.setPageNum(page.getCurrent());
        result.setPageSize(page.getSize());
        return result;
    }

    /**
     * keyword 命中的实例 ID（业务标题/流程名称前缀匹配）；null=不过滤，空列表=无命中
     */
    private List<Long> keywordInstanceIds(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return null;
        }
        String trimmed = keyword.trim();
        return flowInstanceMapper.selectList(new LambdaQueryWrapper<FlowInstanceDO>()
                        .and(wrapper -> wrapper
                                .likeRight(FlowInstanceDO::getBizTitle, trimmed)
                                .or()
                                .likeRight(FlowInstanceDO::getProcessName, trimmed)))
                .stream()
                .map(FlowInstanceDO::getId)
                .distinct()
                .collect(Collectors.toList());
    }

    private Map<Long, FlowInstanceDO> loadInstances(List<FlowTaskDO> records) {
        if (records.isEmpty()) {
            return new HashMap<>(0);
        }
        List<Long> instanceIds = records.stream().map(FlowTaskDO::getInstanceId).distinct()
                .collect(Collectors.toList());
        return flowInstanceMapper.selectBatchIds(instanceIds).stream()
                .collect(Collectors.toMap(FlowInstanceDO::getId, instance -> instance, (a, b) -> a));
    }

    private TaskVO toTaskVO(FlowTaskDO task, FlowInstanceDO instance) {
        TaskVO vo = new TaskVO();
        vo.setId(String.valueOf(task.getId()));
        vo.setInstanceId(String.valueOf(task.getInstanceId()));
        vo.setNodeName(task.getNodeName());
        vo.setMultiMode(task.getMultiMode());
        vo.setSignType(task.getSignType());
        vo.setCreateTime(task.getCreateTime());
        vo.setStatus(task.getStatus());
        vo.setAction(task.getAction());
        vo.setFinishTime(task.getFinishTime());
        vo.setComment(task.getComment());
        if (instance != null) {
            vo.setProcessName(instance.getProcessName());
            vo.setBizType(instance.getBizType());
            vo.setBizId(String.valueOf(instance.getBizId()));
            vo.setBizTitle(instance.getBizTitle());
            vo.setStartUserName(instance.getStartUserName());
        }
        return vo;
    }

    /**
     * 可退回目标：仅 APPROVER 节点且 RETURN 按钮未显式禁用时返回（对齐前端 getRejectTargets 语义）
     */
    private List<ReturnTargetVO> buildReturnTargets(FlowDesignCursor cursor, JsonNode node, FlowTaskDO task) {
        if (node == null || !TYPE_APPROVER.equals(typeOf(node))) {
            return Collections.emptyList();
        }
        if (support.isButtonDisabled(node, ACTION_RETURN)) {
            return Collections.emptyList();
        }
        List<ReturnTargetVO> targets = new ArrayList<>();
        for (JsonNode target : cursor.rejectTargetsBefore(task.getNodeId())) {
            ReturnTargetVO vo = new ReturnTargetVO();
            vo.setNodeId(idOf(target));
            vo.setNodeName(nameOf(target));
            targets.add(vo);
        }
        return targets;
    }
}
