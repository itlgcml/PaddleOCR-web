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
import com.paddleocr.web.model.vo.ApproverButtonVO;
import com.paddleocr.web.model.vo.InstanceDetailVO;
import com.paddleocr.web.model.vo.ManualRouteOptionVO;
import com.paddleocr.web.model.vo.PendingSignTaskVO;
import com.paddleocr.web.model.vo.ReturnTargetVO;
import com.paddleocr.web.model.vo.SubProcessApproverOptionVO;
import com.paddleocr.web.model.vo.TaskDetailVO;
import com.paddleocr.web.model.vo.TaskVO;
import com.paddleocr.web.model.vo.UserBriefVO;
import com.paddleocr.web.service.approval.FlowInstanceService;
import com.paddleocr.web.service.approval.FlowTaskService;
import com.paddleocr.web.service.approval.TaskStatus;
import com.paddleocr.web.service.approval.engine.FlowApproverResolver;
import com.paddleocr.web.service.approval.engine.FlowApproverResolver.Resolved;
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
import static com.paddleocr.web.service.approval.engine.FlowDesignCursor.TYPE_START;
import static com.paddleocr.web.service.approval.engine.FlowDesignCursor.TYPE_SUB_PROCESS;
import static com.paddleocr.web.service.approval.engine.FlowDesignCursor.branchColumns;
import static com.paddleocr.web.service.approval.engine.FlowDesignCursor.chainNext;
import static com.paddleocr.web.service.approval.engine.FlowDesignCursor.firstApproverInBranch;
import static com.paddleocr.web.service.approval.engine.FlowDesignCursor.idOf;
import static com.paddleocr.web.service.approval.engine.FlowDesignCursor.isManualParallel;
import static com.paddleocr.web.service.approval.engine.FlowDesignCursor.isManualRouting;
import static com.paddleocr.web.service.approval.engine.FlowDesignCursor.nameOf;
import static com.paddleocr.web.service.approval.engine.FlowDesignCursor.propsOf;
import static com.paddleocr.web.service.approval.engine.FlowDesignCursor.routeAssigneeTarget;
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
    private static final String ACTION_PASS = "PASS";

    /** 前加签任务派生类型（与 FlowEngineImpl 同值） */
    private static final String SIGN_BEFORE = "BEFORE_SIGN";

    /** 后加签任务派生类型（与 FlowEngineImpl 同值） */
    private static final String SIGN_AFTER = "AFTER_SIGN";

    /** 子流程推进模式：启动后立即继续（与 FlowEngineImpl 同值） */
    private static final String ADVANCE_IMMEDIATE = "IMMEDIATE";

    /** 可指定审批人节点归属：主流程 */
    private static final String SCOPE_MAIN = "MAIN";

    /** 可指定审批人节点归属：子流程 */
    private static final String SCOPE_SUB_PROCESS = "SUB_PROCESS";

    private final FlowTaskMapper flowTaskMapper;
    private final FlowInstanceMapper flowInstanceMapper;
    private final FlowEngineSupport support;
    private final FlowEngine engine;
    private final FlowApproverResolver resolver;
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
        vo.setTask(toTaskVO(task, instance, new HashMap<>(2)));

        // 设计快照按实例口径（§4.4.6）：子实例读自身冗余 subDoc，根实例读版本表
        FlowDesignCursor cursor = support.parseCursorQuietly(
                instance == null ? null : support.loadDesign(instance));
        vo.setButtons(Collections.emptyList());
        vo.setReturnTargets(Collections.emptyList());
        vo.setManualRouteOptions(Collections.emptyList());
        vo.setManualRouteMultiple(false);
        vo.setSubProcessApproverOptions(Collections.emptyList());
        if (cursor != null) {
            JsonNode node = cursor.findNode(task.getNodeId());
            if (SIGN_BEFORE.equals(task.getSignType())) {
                // 前加签任务：仅「同意」+审批意见（评论恒可用），不选路、不退回——PASS 后任务回到原审批人，
                // 由原审批人人工办理（含人工路由选择）；避免加签人误看到节点后继的人工路由选项
                vo.setButtons(passOnlyButtons(node));
            } else {
                vo.setButtons(support.readButtons(node));
                vo.setReturnTargets(buildReturnTargets(cursor, node, task));
                vo.setManualRouteOptions(buildManualRouteOptions(node,
                        instance == null ? null : instance.getStartUserId()));
                vo.setManualRouteMultiple(node != null && isManualParallel(chainNext(node)));
                vo.setSubProcessApproverOptions(buildSubProcessApproverOptions(node, instance));
            }
        }
        vo.setPendingSignTasks(buildPendingSignTasks(task));
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
        Map<Long, String> subProcessNameCache = new HashMap<>(2);
        List<TaskVO> vos = records.stream()
                .map(task -> toTaskVO(task, instanceMap.get(task.getInstanceId()), subProcessNameCache))
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

    private TaskVO toTaskVO(FlowTaskDO task, FlowInstanceDO instance, Map<Long, String> subProcessNameCache) {
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
            vo.setSubProcessName(resolveSubProcessName(instance, subProcessNameCache));
            vo.setBizType(instance.getBizType());
            vo.setBizId(String.valueOf(instance.getBizId()));
            vo.setBizTitle(instance.getBizTitle());
            vo.setStartUserName(instance.getStartUserName());
        }
        return vo;
    }

    /**
     * 子流程节点名（任务所属实例为子流程实例时非空，前端渲染「子流程」徽标）：
     * 从父实例设计快照定位 parentNodeId 节点；按父实例 ID 缓存，避免列表场景重复解析同一父实例
     */
    private String resolveSubProcessName(FlowInstanceDO instance, Map<Long, String> cache) {
        Long parentId = instance.getParentInstanceId();
        if (parentId == null || instance.getParentNodeId() == null) {
            return null;
        }
        if (!cache.containsKey(parentId)) {
            FlowInstanceDO parent = flowInstanceMapper.selectById(parentId);
            FlowDesignCursor cursor = parent == null
                    ? null : support.parseCursorQuietly(support.loadDesign(parent));
            JsonNode node = cursor == null ? null : cursor.findNode(instance.getParentNodeId());
            cache.put(parentId, node == null ? null : nameOf(node));
        }
        return cache.get(parentId);
    }

    /**
     * 前加签任务按钮：仅保留「同意」（沿用节点配置的 PASS 文案与启用状态；未配置时回退默认「同意」）。
     * 评论/抄送为辅助动作由前端恒可用，不经此列表下发；其余办理类按钮（拒绝/退回/转办/委派/加签）
     * 与人工路由均不属于前加签人职责——其 PASS 后任务回到原审批人，由原审批人继续人工办理。
     */
    private List<ApproverButtonVO> passOnlyButtons(JsonNode node) {
        for (ApproverButtonVO button : support.readButtons(node)) {
            if (ACTION_PASS.equals(button.getKey())) {
                return Collections.singletonList(button);
            }
        }
        ApproverButtonVO pass = new ApproverButtonVO();
        pass.setKey(ACTION_PASS);
        pass.setLabel("同意");
        pass.setEnabled(true);
        return Collections.singletonList(pass);
    }

    /**
     * 待办理加签任务：当前任务直接派生（parentTaskId=当前任务）且 PENDING 的前/后加签任务。
     * 发起加签人同意前据此提示「加签人还没有同意」，并可携带 taskId 发起 REDUCE_SIGN 减签；
     * 加签任务自身无派生链查询意义（其上再加签属链上加签，由该加签人自己办理），返回空。
     */
    private List<PendingSignTaskVO> buildPendingSignTasks(FlowTaskDO task) {
        if (task.getId() == null) {
            return Collections.emptyList();
        }
        List<FlowTaskDO> signs = flowTaskMapper.selectList(new LambdaQueryWrapper<FlowTaskDO>()
                .eq(FlowTaskDO::getInstanceId, task.getInstanceId())
                .eq(FlowTaskDO::getParentTaskId, task.getId())
                .eq(FlowTaskDO::getStatus, TaskStatus.PENDING)
                .in(FlowTaskDO::getSignType, Arrays.asList(SIGN_BEFORE, SIGN_AFTER)));
        List<PendingSignTaskVO> vos = new ArrayList<>(signs.size());
        for (FlowTaskDO sign : signs) {
            PendingSignTaskVO vo = new PendingSignTaskVO();
            vo.setTaskId(String.valueOf(sign.getId()));
            vo.setSignType(sign.getSignType());
            vo.setAssigneeId(String.valueOf(sign.getAssigneeId()));
            vo.setAssigneeName(sign.getAssigneeName());
            vo.setCreateTime(sign.getCreateTime());
            vos.add(vo);
        }
        return vos;
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

    /**
     * 人工路由选项：当前任务节点（APPROVER）的链后继若为人工路由（CONDITIONS MANUAL，单选）或
     * 人工并行（PARALLEL MANUAL，多选）节点，则返回其全部分支列选项（branchId=分支列 id，
     * label=选项名——条件分支列取 props.label，并行分支列无 label 回退列名）；
     * assigneeOptions=指定审批人目标节点的候选审批人（可据此在 PASS 时指定该分支的审批人）。
     * 否则返回空列表。
     */
    private List<ManualRouteOptionVO> buildManualRouteOptions(JsonNode node, Long startUserId) {
        if (node == null) {
            return Collections.emptyList();
        }
        JsonNode next = chainNext(node);
        if (!isManualRouting(next) && !isManualParallel(next)) {
            return Collections.emptyList();
        }
        List<ManualRouteOptionVO> options = new ArrayList<>();
        for (JsonNode column : branchColumns(next)) {
            ManualRouteOptionVO option = new ManualRouteOptionVO();
            option.setBranchId(idOf(column));
            String label = propsOf(column).path("label").asText("");
            option.setLabel(label.isEmpty() ? nameOf(column) : label);
            option.setAssigneeOptions(buildAssigneeOptions(column, next, startUserId));
            options.add(option);
        }
        return options;
    }

    /**
     * 指定审批人目标节点的候选审批人：优先分支列内首个审批节点；列内无审批节点（空列或
     * 仅直通办理节点，引擎推进将汇合到容器后继）时回退容器后继链上的首个审批节点，
     * 与引擎流转落点及覆盖登记（routeAssigneeTarget）同口径，保证候选集即校验集。
     * 定位到目标后按节点配置解析（与引擎 resolve 同口径）；遇嵌套分支容器或链尾无审批
     * 节点返回空列表——审批人不唯一/不存在，前端不渲染指定审批人下拉，运行时按默认解析。
     * 候选为空（如 LEADER/SELF_SELECT 降级）同样返回空列表，运行时仍按 emptyPolicy 兜底。
     */
    private List<UserBriefVO> buildAssigneeOptions(JsonNode column, JsonNode container, Long startUserId) {
        JsonNode firstApprover = routeAssigneeTarget(column, container);
        return firstApprover == null ? Collections.emptyList() : resolveAssignees(firstApprover, startUserId);
    }

    /**
     * 解析审批节点的候选审批人（与引擎 resolve 同口径）；候选为空（如 LEADER/SELF_SELECT 降级）
     * 返回空列表，运行时仍按 emptyPolicy 兜底。
     */
    private List<UserBriefVO> resolveAssignees(JsonNode approver, Long startUserId) {
        if (startUserId == null) {
            return Collections.emptyList();
        }
        Resolved resolved = resolver.resolve(approver, startUserId);
        List<UserBriefVO> users = new ArrayList<>(resolved.getUsers().size());
        for (UserDO user : resolved.getUsers()) {
            UserBriefVO brief = new UserBriefVO();
            brief.setUserId(String.valueOf(user.getId()));
            brief.setName(FlowApproverResolver.displayName(user));
            users.add(brief);
        }
        return users;
    }

    /**
     * 子流程下一节点可指定审批人目标：当前任务节点（APPROVER）的链后继若为子流程（SUB_PROCESS）
     * 且推进模式为 IMMEDIATE（启动后继续），则返回两个目标——子流程内部首个审批节点（SUB_PROCESS
     * 作用域）+ 父链下一审批节点（MAIN 作用域），PASS 弹窗据此分开展示并分别指定审批人。
     * 任一目标定位不到（子 doc/父链无审批节点或遇嵌套分支容器，审批人不唯一）则该侧省略。
     * 否则返回空列表。
     */
    private List<SubProcessApproverOptionVO> buildSubProcessApproverOptions(JsonNode node, FlowInstanceDO instance) {
        if (node == null || instance == null) {
            return Collections.emptyList();
        }
        JsonNode subProcessNode = chainNext(node);
        if (subProcessNode == null || !TYPE_SUB_PROCESS.equals(typeOf(subProcessNode))
                || !ADVANCE_IMMEDIATE.equals(propsOf(subProcessNode).path("advanceMode").asText("WAIT"))) {
            return Collections.emptyList();
        }
        JsonNode subDoc = propsOf(subProcessNode).path("subDoc");
        if (!subDoc.isObject() || !TYPE_START.equals(typeOf(subDoc.path("root")))) {
            return Collections.emptyList();
        }
        Long startUserId = instance.getStartUserId();
        List<SubProcessApproverOptionVO> options = new ArrayList<>();
        // 子流程内部首个审批节点（子 doc 链首）
        JsonNode subFirst = firstApproverInBranch(subDoc.path("root"));
        if (subFirst != null) {
            options.add(buildSubProcessOption(subFirst, SCOPE_SUB_PROCESS, nameOf(subProcessNode), startUserId));
        }
        // 父链下一审批节点（SUB_PROCESS.childNode 链首）
        JsonNode parentFirst = firstApproverInBranch(subProcessNode);
        if (parentFirst != null) {
            options.add(buildSubProcessOption(parentFirst, SCOPE_MAIN, instance.getProcessName(), startUserId));
        }
        return options;
    }

    private SubProcessApproverOptionVO buildSubProcessOption(JsonNode approver, String scopeType,
                                                             String scopeName, Long startUserId) {
        SubProcessApproverOptionVO option = new SubProcessApproverOptionVO();
        option.setNodeId(idOf(approver));
        option.setNodeName(nameOf(approver));
        option.setScopeType(scopeType);
        option.setScopeName(scopeName);
        option.setAssigneeOptions(resolveAssignees(approver, startUserId));
        return option;
    }
}
