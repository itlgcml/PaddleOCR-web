package com.paddleocr.web.service.approval.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.paddleocr.web.common.BusinessException;
import com.paddleocr.web.common.ResultCode;
import com.paddleocr.web.mapper.entity.FlowInstanceDO;
import com.paddleocr.web.mapper.entity.FlowTaskDO;
import com.paddleocr.web.mapper.entity.ProcessDO;
import com.paddleocr.web.mapper.entity.ProcessVersionDO;
import com.paddleocr.web.mapper.entity.UserDO;
import com.paddleocr.web.model.dto.TaskActionDTO;
import com.paddleocr.web.model.vo.NextApproverVO;
import com.paddleocr.web.model.vo.NodeApproverPreviewVO;
import com.paddleocr.web.model.vo.UserBriefVO;
import com.paddleocr.web.service.approval.ApprovalRuntimeProperties;
import com.paddleocr.web.service.approval.InstanceStatus;
import com.paddleocr.web.service.approval.TaskAction;
import com.paddleocr.web.service.approval.TaskStatus;
import com.paddleocr.web.service.approval.engine.FlowApproverResolver.Resolved;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static com.paddleocr.web.service.approval.engine.FlowDesignCursor.TYPE_CONDITIONS;
import static com.paddleocr.web.service.approval.engine.FlowDesignCursor.TYPE_PARALLEL;
import static com.paddleocr.web.service.approval.engine.FlowDesignCursor.TYPE_START;
import static com.paddleocr.web.service.approval.engine.FlowDesignCursor.TYPE_TRANSACT;
import static com.paddleocr.web.service.approval.engine.FlowDesignCursor.branchColumns;
import static com.paddleocr.web.service.approval.engine.FlowDesignCursor.chainNext;
import static com.paddleocr.web.service.approval.engine.FlowDesignCursor.idOf;
import static com.paddleocr.web.service.approval.engine.FlowDesignCursor.isManualParallel;
import static com.paddleocr.web.service.approval.engine.FlowDesignCursor.isManualRouting;
import static com.paddleocr.web.service.approval.engine.FlowDesignCursor.nameOf;
import static com.paddleocr.web.service.approval.engine.FlowDesignCursor.propsOf;
import static com.paddleocr.web.service.approval.engine.FlowDesignCursor.routeAssigneeTarget;
import static com.paddleocr.web.service.approval.engine.FlowDesignCursor.typeOf;

/**
 * 审批流引擎实现：主干推进 / 条件路由 / 并行汇合 / 多实例判定 / 退回与重新提交 / 加签转办 / 撤销
 *
 * <p>并发控制：实例级推进锁（tryLock 不等待 + leaseTime 兜底），锁内以 TransactionTemplate 提交后才释放锁，
 * 规避"锁先释放、事务后提交"窗口期内另一请求读到旧任务集重复推进。</p>
 *
 * <p>多实例与加签统一模型：根任务（parent=NULL）按 multiMode 判定节点完成；加签任务 PENDING 期间
 * 挂起源任务（BEFORE：加签人先审后原审批人；AFTER：原审批人先审后加签人）；AUTO_PASS 生成 PASSED
 * 伪任务（assignee=0/系统自动），保证并行汇合与进度判定口径统一。</p>
 *
 * <p>审批记录口径：每次操作（发起/办理/撤销）仅落一条 flow_task_log——动作先 stageActionLog 暂存，
 * 推进过程中的系统说明（降级/自动通过/汇合/退回等）经 addEngineNote 收集，flushActionLog 聚合
 * 拼入该记录 detail（"；"分隔，条目带【节点名】前缀），不再单独成记录。</p>
 *
 * @author paddleocr
 * @date 2026-10-12
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FlowEngineImpl implements FlowEngine {

    private static final String LOCK_KEY_PREFIX = "approval:instance:advance:";

    /** tryLock 不等待：获取失败立即失败（并发动作由后到者收到任务已被处理） */
    private static final long LOCK_WAIT_SECONDS = 0L;

    private static final String SIGN_NORMAL = "NORMAL";
    private static final String SIGN_BEFORE = "BEFORE_SIGN";
    private static final String SIGN_AFTER = "AFTER_SIGN";
    private static final String SIGN_TRANSFER = "TRANSFER";
    private static final String SIGN_DELEGATE = "DELEGATE";

    private static final String MULTI_AND = "AND";
    private static final String MULTI_OR = "OR";

    private final FlowEngineSupport support;
    private final FlowApproverResolver resolver;
    private final RedissonClient redissonClient;
    private final ApprovalRuntimeProperties properties;
    private final TransactionTemplate transactionTemplate;

    // ==================== 接口实现 ====================

    @Override
    public Long startInstance(ProcessDO process, ProcessVersionDO version, String bizType, Long bizId,
                              UserDO startUser, String bizTitle, String formJson,
                              List<UserDO> ccUsers, String startComment) {
        if (formJson != null && formJson.length() > properties.getSnapshotMaxLength()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "表单快照超过长度上限");
        }
        return transactionTemplate.execute(status ->
                doStartInstance(process, version, bizType, bizId, startUser, bizTitle, formJson, ccUsers, startComment));
    }

    private Long doStartInstance(ProcessDO process, ProcessVersionDO version, String bizType, Long bizId,
                                 UserDO startUser, String bizTitle, String formJson,
                                 List<UserDO> ccUsers, String startComment) {
        FlowInstanceDO instance = new FlowInstanceDO();
        instance.setProcessId(process.getId());
        instance.setProcessCode(process.getCode());
        instance.setProcessName(process.getName());
        instance.setProcessVersion(version.getVersion());
        instance.setProcessVersionId(version.getId());
        instance.setBizType(bizType);
        instance.setBizId(bizId);
        instance.setBizTitle(bizTitle);
        instance.setFormSnapshot(formJson);
        instance.setStartUserId(startUser.getId());
        instance.setStartUserName(FlowApproverResolver.displayName(startUser));
        instance.setStatus(InstanceStatus.RUNNING);
        support.insertInstance(instance);

        EngineCtx ctx = support.loadCtx(instance, version.getDesignJson());
        support.stageActionLog(ctx, null, ctx.getCursor().root(), TaskAction.START, startUser, startComment, null);
        support.saveCc(ctx, null, ccUsers);
        advanceFrom(chainNext(ctx.getCursor().root()), null, ctx);
        support.flushActionLog(ctx);
        log.info("审批实例发起成功, instanceId={}, processCode={}, bizType={}, bizId={}",
                instance.getId(), process.getCode(), bizType, bizId);
        return instance.getId();
    }

    @Override
    public void handleAction(Long taskId, TaskActionDTO dto, UserDO operator) {
        FlowTaskDO task = support.requireTask(taskId);
        executeWithAdvanceLock(task.getInstanceId(), () -> {
            transactionTemplate.executeWithoutResult(status -> doHandleAction(task, dto, operator));
            return null;
        });
    }

    private void doHandleAction(FlowTaskDO task, TaskActionDTO dto, UserDO operator) {
        FlowInstanceDO instance = support.requireInstance(task.getInstanceId());
        if (!InstanceStatus.RUNNING.equals(instance.getStatus())) {
            throw new BusinessException(ResultCode.INSTANCE_STATUS_ILLEGAL);
        }
        if (!task.getAssigneeId().equals(operator.getId())) {
            throw new BusinessException(ResultCode.TASK_ASSIGNEE_MISMATCH);
        }
        if (!TaskStatus.PENDING.equals(task.getStatus())) {
            throw new BusinessException(ResultCode.TASK_ALREADY_DONE);
        }
        EngineCtx ctx = support.loadCtx(instance, null);
        JsonNode node = ctx.getCursor().findNode(task.getNodeId());
        // 前加签任务仅「同意」（+评论/抄送辅助动作）：PASS 后任务回到原审批人，由原审批人继续办理；
        // 拒绝/退回/转办/委派/再加签不属于前加签人职责（与 TaskDetailVO.passOnlyButtons 口径一致）
        if (SIGN_BEFORE.equals(task.getSignType())
                && !TaskAction.PASS.equals(dto.getAction())
                && !TaskAction.COMMENT.equals(dto.getAction())
                && !TaskAction.COPY.equals(dto.getAction())) {
            throw new BusinessException(ResultCode.TASK_ACTION_DISABLED, "前加签任务仅允许同意或评论");
        }
        switch (dto.getAction()) {
            case TaskAction.PASS:
                doPass(task, dto, operator, ctx, node);
                break;
            case TaskAction.REJECT:
                doReject(task, dto, operator, ctx, node);
                break;
            case TaskAction.RETURN:
                doReturn(task, dto, operator, ctx, node);
                break;
            case TaskAction.TRANSFER:
            case TaskAction.DELEGATE:
                doTransferOrDelegate(task, dto, operator, ctx, node);
                break;
            case TaskAction.ADD_SIGN:
                doAddSign(task, dto, operator, ctx, node);
                break;
            case TaskAction.REDUCE_SIGN:
                doReduceSign(task, dto, operator, ctx, node);
                break;
            case TaskAction.COMMENT:
                support.stageActionLog(ctx, task, node, TaskAction.COMMENT, operator, dto.getComment(), null);
                break;
            case TaskAction.COPY:
                doCopy(task, dto, operator, ctx, node);
                break;
            default:
                throw new BusinessException(ResultCode.PARAM_ERROR, "不支持的动作类型");
        }
        // 每次操作仅落一条审批记录：动作 + 推进系统说明聚合后统一落库
        support.flushActionLog(ctx);
    }

    @Override
    public void cancelInstance(Long instanceId, UserDO operator, String comment) {
        executeWithAdvanceLock(instanceId, () -> {
            transactionTemplate.executeWithoutResult(status -> {
                FlowInstanceDO instance = support.requireInstance(instanceId);
                if (!InstanceStatus.RUNNING.equals(instance.getStatus())) {
                    throw new BusinessException(ResultCode.INSTANCE_STATUS_ILLEGAL);
                }
                EngineCtx ctx = support.loadCtx(instance, null);
                for (FlowTaskDO pendingTask : support.loadPendingTasks(instanceId)) {
                    support.finishTask(pendingTask, TaskStatus.CANCELED, null, null);
                }
                support.finishInstance(ctx, InstanceStatus.CANCELED);
                support.stageActionLog(ctx, null, null, TaskAction.CANCEL, operator, comment, "撤销实例，待办任务全部取消");
                support.flushActionLog(ctx);
            });
            return null;
        });
    }

    @Override
    public NextApproverVO previewNextApprovers(Long processId, String nodeId, Long currentUserId,
                                               Map<String, String> conditions) {
        ProcessVersionDO version = support.requirePublishedVersion(processId);
        FlowDesignCursor cursor = support.parseCursor(version.getDesignJson());
        JsonNode start;
        if (nodeId == null || nodeId.isEmpty()) {
            start = chainNext(cursor.root());
        } else {
            JsonNode current = cursor.findNode(nodeId);
            if (current == null) {
                throw new BusinessException(ResultCode.PROCESS_NODE_NOT_FOUND);
            }
            start = nextPositionOf(current, cursor);
        }
        List<NodeApproverPreviewVO> nodes = new ArrayList<>();
        if (start != null) {
            dryRunCollect(start, cursor, conditions, currentUserId, nodes);
        }
        NextApproverVO vo = new NextApproverVO();
        vo.setEnded(nodes.isEmpty());
        vo.setNodes(nodes);
        return vo;
    }

    // ==================== 动作实现 ====================

    private void doPass(FlowTaskDO task, TaskActionDTO dto, UserDO operator, EngineCtx ctx, JsonNode node) {
        boolean isSubmit = node != null && TYPE_START.equals(typeOf(node));
        if (!isSubmit) {
            support.requireButtonEnabled(node, "PASS");
        }
        String action = isSubmit ? TaskAction.SUBMIT : TaskAction.PASS;
        support.finishTask(task, TaskStatus.PASSED, action, dto.getComment());
        support.stageActionLog(ctx, task, node, action, operator, dto.getComment(), null);

        // 前加签人通过：解除挂起即止，等待根任务（原审批人）人工办理
        if (SIGN_BEFORE.equals(task.getSignType())) {
            return;
        }
        if (!nodeCompleted(ctx, task.getNodeId())) {
            return;
        }
        support.cancelOtherPendingRootTasks(ctx, task.getNodeId(), task.getId());
        if (node == null) {
            return;
        }
        JsonNode next = chainNext(node);
        if (isManualRouting(next)) {
            // 下一节点为人工路由：由本审批人在 PASS 时携带 routeTargetId 选定分支列（可经 routeAssignees 指定目标审批节点的审批人，空列回退容器后继节点）
            advanceManualRoute(next, dto.getRouteTargetId(), dto.getRouteAssignees(), ctx);
        } else if (isManualParallel(next)) {
            // 下一节点为人工并行：由本审批人在 PASS 时携带 routeTargetIds 勾选要执行的分支列（多选，可指定各目标审批节点的审批人，空列回退容器后继节点）
            advanceManualParallel(next, dto.getRouteTargetIds(), dto.getRouteAssignees(), ctx);
        } else {
            advanceFrom(next, ctx.getCursor().findBranchHost(idOf(node)), ctx);
        }
    }

    /**
     * 人工路由推进：校验并定位 PASS 携带的分支列，从其链首推进；空分支列直通（汇合到容器后继）。
     * 与 AUTO 条件分支的单选语义一致——命中列走完即容器完成，advance 容器 childNode。
     * routeAssignees 携带指定审批人时登记一次性覆盖（目标=列内首个审批节点，空列回退容器后继
     * 链首个审批节点，与流转落点一致），建任务时替代默认解析。
     */
    private void advanceManualRoute(JsonNode conditionsNode, String routeTargetId,
                                    Map<String, String> routeAssignees, EngineCtx ctx) {
        if (routeTargetId == null || routeTargetId.isEmpty()) {
            throw new BusinessException(ResultCode.TASK_MANUAL_ROUTE_REQUIRED);
        }
        JsonNode chosen = null;
        for (JsonNode column : branchColumns(conditionsNode)) {
            if (routeTargetId.equals(idOf(column))) {
                chosen = column;
                break;
            }
        }
        if (chosen == null) {
            throw new BusinessException(ResultCode.TASK_MANUAL_ROUTE_INVALID);
        }
        JsonNode branchFirst = chainNext(chosen);
        applyRouteAssignee(chosen, conditionsNode, routeAssignees, ctx);
        if (branchFirst != null) {
            advanceFrom(branchFirst, ctx.getCursor().findBranchHost(idOf(chosen)), ctx);
        } else {
            advanceFrom(chainNext(conditionsNode), ctx.getCursor().findBranchHost(idOf(conditionsNode)), ctx);
        }
    }

    /**
     * 人工并行推进：校验 PASS 勾选的分支列集合（至少一个、均须为该容器的分支列），
     * 仅对选中列并行推进；未选中列不建任务——汇合按 PENDING 任务判定，天然跳过不阻塞。
     * 空分支列直接参与汇合判定（与 AUTO 并行的空列处理一致）。
     * routeAssignees 携带某选中列的指定审批人时登记一次性覆盖（目标=列内首个审批节点，空列回退
     * 容器后继链首个审批节点；多空列回退同一后继时首个登记生效；未选中列的指定忽略）。
     */
    private void advanceManualParallel(JsonNode parallelNode, List<String> routeTargetIds,
                                       Map<String, String> routeAssignees, EngineCtx ctx) {
        if (routeTargetIds == null || routeTargetIds.isEmpty()) {
            throw new BusinessException(ResultCode.TASK_MANUAL_ROUTE_REQUIRED);
        }
        Set<String> requested = new LinkedHashSet<>(routeTargetIds);
        List<JsonNode> chosen = new ArrayList<>();
        for (JsonNode column : branchColumns(parallelNode)) {
            if (requested.remove(idOf(column))) {
                chosen.add(column);
            }
        }
        if (!requested.isEmpty() || chosen.isEmpty()) {
            // 存在指向流程外或非该容器分支列的非法 id
            throw new BusinessException(ResultCode.TASK_MANUAL_ROUTE_INVALID);
        }
        for (JsonNode column : chosen) {
            JsonNode first = chainNext(column);
            applyRouteAssignee(column, parallelNode, routeAssignees, ctx);
            if (first != null) {
                advanceFrom(first, ctx.getCursor().findBranchHost(idOf(column)), ctx);
            } else {
                tryMergeParallel(ctx.getCursor().findBranchHost(idOf(column)), ctx);
            }
        }
    }

    /**
     * 登记指定审批人的目标节点（nodeId → userId，一次性消费）：优先分支列内首个审批节点；
     * 列内无审批节点（空列或仅直通办理节点，推进将汇合到容器后继）时回退容器后继链上的
     * 首个审批节点（routeAssigneeTarget 与空列流转落点同口径）；遇嵌套分支容器即定位不到
     * ——审批人不唯一，静默忽略；定位不到或未携带 userId 时忽略。列内审批节点前的办理节点
     * 由 advanceFrom 直通跳过，覆盖在本次推进内即被消费。人工并行多空列回退到同一后继节点时
     * 首个登记生效（putIfAbsent），避免列间互相覆盖。
     */
    private void applyRouteAssignee(JsonNode column, JsonNode container,
                                    Map<String, String> routeAssignees, EngineCtx ctx) {
        if (routeAssignees == null) {
            return;
        }
        JsonNode firstApprover = routeAssigneeTarget(column, container);
        if (firstApprover == null) {
            return;
        }
        String userId = routeAssignees.get(idOf(column));
        if (userId == null || userId.isEmpty()) {
            return;
        }
        long parsed;
        try {
            parsed = Long.parseLong(userId);
        } catch (NumberFormatException e) {
            throw new BusinessException(ResultCode.TASK_RECEIVER_INVALID, "指定审批人 ID 不合法");
        }
        ctx.getAssigneeOverrides().putIfAbsent(idOf(firstApprover), parsed);
    }

    private void doReject(FlowTaskDO task, TaskActionDTO dto, UserDO operator, EngineCtx ctx, JsonNode node) {
        requireComment(dto, "拒绝");
        support.requireButtonEnabled(node, "REJECT");
        support.finishTask(task, TaskStatus.REJECTED, TaskAction.REJECT, dto.getComment());
        support.cancelPendingTasks(ctx, task.getNodeId());
        support.stageActionLog(ctx, task, node, TaskAction.REJECT, operator, dto.getComment(), null);

        JsonNode props = node == null ? null : propsOf(node);
        boolean backToStart = props != null && props.path("canRejectToStart").asBoolean(false);
        String rejectTargetId = props == null ? "" : props.path("rejectTargetId").asText("");
        if (backToStart) {
            backToStart(ctx, "拒绝退回，发起人重新提交");
        } else if (rejectTargetId != null && !rejectTargetId.isEmpty()) {
            JsonNode target = requireReturnTarget(ctx, rejectTargetId);
            backToNode(ctx, target, "拒绝退回到【" + nameOf(target) + "】");
        } else {
            support.finishInstance(ctx, InstanceStatus.REJECTED);
        }
    }

    private void doReturn(FlowTaskDO task, TaskActionDTO dto, UserDO operator, EngineCtx ctx, JsonNode node) {
        requireButtonEnabledReturn(node);
        if (dto.getTargetNodeId() == null || dto.getTargetNodeId().isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "退回目标节点不能为空");
        }
        requireComment(dto, "退回");
        JsonNode target = requireReturnTarget(ctx, dto.getTargetNodeId());
        support.finishTask(task, TaskStatus.REJECTED, TaskAction.RETURN, dto.getComment());
        support.cancelPendingTasks(ctx, task.getNodeId());
        support.stageActionLog(ctx, task, node, TaskAction.RETURN, operator, dto.getComment(),
                "退回到【" + nameOf(target) + "】");
        backToNode(ctx, target, null);
    }

    private void doTransferOrDelegate(FlowTaskDO task, TaskActionDTO dto, UserDO operator,
                                      EngineCtx ctx, JsonNode node) {
        boolean delegate = TaskAction.DELEGATE.equals(dto.getAction());
        support.requireButtonEnabled(node, delegate ? "DELEGATE" : "TRANSFER");
        UserDO receiver = support.requireUser(dto.getReceiverId());
        String action = delegate ? TaskAction.DELEGATE : TaskAction.TRANSFER;
        support.finishTask(task, TaskStatus.TRANSFERRED, action, dto.getComment());
        support.stageActionLog(ctx, task, node, action, operator, dto.getComment(),
                (delegate ? "委派给【" : "转办给【") + FlowApproverResolver.displayName(receiver) + "】");
        FlowTaskDO next = support.newTask(ctx, node, receiver, MULTI_OR,
                delegate ? SIGN_DELEGATE : SIGN_TRANSFER, null);
        log.info("任务转办/委派, fromTaskId={}, toTaskId={}, receiverId={}",
                task.getId(), next.getId(), receiver.getId());
    }

    private void doAddSign(FlowTaskDO task, TaskActionDTO dto, UserDO operator, EngineCtx ctx, JsonNode node) {
        boolean before = TaskAction.SIGN_BEFORE.equals(dto.getDirection());
        if (dto.getDirection() == null || (!before && !TaskAction.SIGN_AFTER.equals(dto.getDirection()))) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "加签方向不能为空");
        }
        support.requireButtonEnabled(node, before ? "ADD_SIGN_BEFORE" : "ADD_SIGN_AFTER");
        UserDO receiver = support.requireUser(dto.getReceiverId());
        FlowTaskDO signTask = support.newTask(ctx, node, receiver, MULTI_OR,
                before ? SIGN_BEFORE : SIGN_AFTER, task.getId());
        support.stageActionLog(ctx, task, node, TaskAction.ADD_SIGN, operator, dto.getComment(),
                (before ? "向前加签【" : "向后加签【") + FlowApproverResolver.displayName(receiver) + "】");
        log.info("任务加签, sourceTaskId={}, signTaskId={}, receiverId={}",
                task.getId(), signTask.getId(), receiver.getId());
    }

    /**
     * 减签：发起加签人（当前任务 assignee，任务 PENDING 已由入口校验）删除自己直接派生且
     * 未办理（PENDING）的前/后加签任务；目标任务之下若还有 PENDING 链上加签子任务则级联取消。
     * 当前任务仍 PENDING → 节点必然未完成，取消挂起衍生任务无需推进判定：
     * 前加签链清空即原任务解冻可办；后加签删除后原任务 PASS 时按正常链路推进。
     */
    private void doReduceSign(FlowTaskDO task, TaskActionDTO dto, UserDO operator, EngineCtx ctx, JsonNode node) {
        Long targetId;
        try {
            targetId = Long.parseLong(dto.getTargetTaskId());
        } catch (NumberFormatException e) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "减签目标任务 ID 不合法");
        }
        FlowTaskDO target = support.requireTask(targetId);
        if (!task.getInstanceId().equals(target.getInstanceId())
                || !task.getId().equals(target.getParentTaskId())
                || !TaskStatus.PENDING.equals(target.getStatus())
                || (!SIGN_BEFORE.equals(target.getSignType()) && !SIGN_AFTER.equals(target.getSignType()))) {
            throw new BusinessException(ResultCode.TASK_REDUCE_SIGN_INVALID);
        }
        int removed = cancelSignChain(ctx, target);
        support.stageActionLog(ctx, task, node, TaskAction.REDUCE_SIGN, operator, dto.getComment(),
                "减签，移除加签人【" + target.getAssigneeName() + "】" + (removed > 1 ? "及其加签链共 " + removed + " 项" : ""));
        log.info("任务减签, sourceTaskId={}, removedTaskIds={}, removed={}",
                task.getId(), targetId, removed);
    }

    /** 级联取消目标任务及其下全部 PENDING 加签子任务（链上加签一并减掉），返回取消条数 */
    private int cancelSignChain(EngineCtx ctx, FlowTaskDO target) {
        int count = 1;
        for (FlowTaskDO child : support.loadTasks(ctx, target.getNodeId())) {
            if (target.getId().equals(child.getParentTaskId())
                    && TaskStatus.PENDING.equals(child.getStatus())) {
                count += cancelSignChain(ctx, child);
            }
        }
        support.finishTask(target, TaskStatus.CANCELED, TaskAction.REDUCE_SIGN, null);
        return count;
    }

    private void doCopy(FlowTaskDO task, TaskActionDTO dto, UserDO operator, EngineCtx ctx, JsonNode node) {
        if (dto.getReceiverIds() == null || dto.getReceiverIds().isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "抄送人不能为空");
        }
        if (dto.getReceiverIds().size() > properties.getMaxReceivers()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "抄送人数量超过上限");
        }
        List<UserDO> receivers = dto.getReceiverIds().stream()
                .map(support::requireUser)
                .collect(java.util.stream.Collectors.toList());
        support.saveCc(ctx, task.getId(), receivers);
        support.stageActionLog(ctx, task, node, TaskAction.COPY, operator, dto.getComment(),
                "抄送给【" + FlowEngineSupport.joinNames(receivers) + "】");
    }

    private void requireComment(TaskActionDTO dto, String scene) {
        if (dto.getComment() == null || dto.getComment().trim().isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, scene + "意见不能为空");
        }
    }

    private void requireButtonEnabledReturn(JsonNode node) {
        support.requireButtonEnabled(node, "RETURN");
    }

    // ==================== 退回与重新提交 ====================

    /**
     * 退回目标校验：必须存在于流程且位于当前节点先序路径之前（START/APPROVER）→ 否则 40056
     */
    private JsonNode requireReturnTarget(EngineCtx ctx, String targetNodeId) {
        JsonNode target = ctx.getCursor().findNode(targetNodeId);
        if (target == null) {
            throw new BusinessException(ResultCode.TASK_RETURN_TARGET_INVALID);
        }
        return target;
    }

    /**
     * 退回发起人：root 节点生成发起人重提交任务（SUBMIT 通过后从 root 后继重新推进）
     */
    private void backToStart(EngineCtx ctx, String detail) {
        JsonNode root = ctx.getCursor().root();
        UserDO startUser = support.requireUserById(ctx.getInstance().getStartUserId());
        support.addEngineNote(ctx, root, detail);
        support.newTask(ctx, root, startUser, MULTI_OR, SIGN_NORMAL, null);
    }

    /**
     * 退回指定节点：重新解析审批人并生成任务组（空审批人走 emptyPolicy，AUTO_PASS 则继续向后推进）
     */
    private void backToNode(EngineCtx ctx, JsonNode target, String detail) {
        if (TYPE_START.equals(typeOf(target))) {
            backToStart(ctx, detail == null ? "退回发起人重新提交" : detail);
            return;
        }
        if (detail != null) {
            support.addEngineNote(ctx, target, detail);
        }
        resolveApproverAndCreate(target, ctx);
    }

    // ==================== 推进内核 ====================

    /**
     * 从 position 向后推进：直通节点跳过、条件路由、并行分派、审批节点生成任务即停；
     * 到达链尾：分支内触发汇合判定，主干尾完成实例
     */
    private void advanceFrom(JsonNode position, FlowDesignCursor.BranchHost host, EngineCtx ctx) {
        JsonNode node = position;
        while (node != null) {
            String type = typeOf(node);
            if (TYPE_START.equals(type) || TYPE_TRANSACT.equals(type)) {
                node = chainNext(node);
                continue;
            }
            if (TYPE_CONDITIONS.equals(type)) {
                if (isManualRouting(node)) {
                    // 上游非审批节点直连（发起直推/分支汇合后）：无人在 PASS 时选列，降级为自动路由 + 系统说明
                    support.addEngineNote(ctx, node, "人工路由节点上游无审批人选择，降级为自动路由");
                }
                JsonNode branch = ctx.getCursor().selectConditionBranch(node, ctx.getFormValues());
                JsonNode branchFirst = branch != null ? chainNext(branch) : null;
                if (branchFirst != null) {
                    advanceFrom(branchFirst, ctx.getCursor().findBranchHost(idOf(branch)), ctx);
                } else {
                    advanceFrom(chainNext(node), host, ctx);
                }
                return;
            }
            if (TYPE_PARALLEL.equals(type)) {
                if (isManualParallel(node)) {
                    // 上游非审批节点直连：无人在 PASS 时勾选，降级为全列并行 + 系统说明
                    support.addEngineNote(ctx, node, "人工并行节点上游无审批人勾选，降级为全列并行");
                }
                for (JsonNode column : branchColumns(node)) {
                    JsonNode first = chainNext(column);
                    if (first != null) {
                        advanceFrom(first, ctx.getCursor().findBranchHost(idOf(column)), ctx);
                    } else {
                        tryMergeParallel(ctx.getCursor().findBranchHost(idOf(column)), ctx);
                    }
                }
                return;
            }
            // APPROVER
            resolveApproverAndCreate(node, ctx);
            return;
        }
        if (host != null) {
            tryMergeParallel(host, ctx);
        } else {
            support.finishInstance(ctx, InstanceStatus.COMPLETED);
        }
    }

    /**
     * 审批节点处理：解析审批人；空则按 emptyPolicy 兜底（说明记 ENGINE 日志，AUTO_PASS 落伪任务并继续推进）。
     * 人工路由/人工并行 PASS 指定的审批人（ctx.assigneeOverrides）优先于默认解析：
     * 须为该节点候选审批人之一，命中后仅为其创建单人任务（OR 模式）并记 ENGINE 日志。
     */
    private void resolveApproverAndCreate(JsonNode node, EngineCtx ctx) {
        Resolved resolved = resolver.resolve(node, ctx.getInstance().getStartUserId());
        for (String note : resolved.getEngineNotes()) {
            support.addEngineNote(ctx, node, note);
        }
        Long overrideUserId = ctx.getAssigneeOverrides().remove(idOf(node));
        if (overrideUserId != null) {
            UserDO assigned = resolved.getUsers().stream()
                    .filter(user -> overrideUserId.equals(user.getId()))
                    .findFirst()
                    .orElseThrow(() -> new BusinessException(ResultCode.TASK_RECEIVER_INVALID,
                            "指定审批人不在该节点的候选审批人范围内"));
            support.addEngineNote(ctx, node, "人工路由指定审批人：" + FlowApproverResolver.displayName(assigned));
            support.createTasks(ctx, node, Collections.singletonList(assigned), MULTI_OR);
            return;
        }
        if (!resolved.getUsers().isEmpty()) {
            String multiMode = propsOf(node).path("multiMode").asText(MULTI_OR);
            support.createTasks(ctx, node, resolved.getUsers(), MULTI_AND.equals(multiMode) ? MULTI_AND : MULTI_OR);
            return;
        }
        support.addEngineNote(ctx, node, "节点无可用审批人，自动通过");
        support.createAutoPassedTask(ctx, node);
        advanceFrom(chainNext(node), ctx.getCursor().findBranchHost(idOf(node)), ctx);
    }

    /**
     * 并行汇合判定：容器全部分支链均无 PENDING 任务 → 从容器后继继续推进（嵌套容器递归向上）
     */
    private void tryMergeParallel(FlowDesignCursor.BranchHost host, EngineCtx ctx) {
        if (host == null) {
            support.finishInstance(ctx, InstanceStatus.COMPLETED);
            return;
        }
        for (JsonNode column : branchColumns(host.getContainer())) {
            if (support.hasPendingTasks(ctx, ctx.getCursor().collectBranchNodeIds(column))) {
                return;
            }
        }
        JsonNode container = host.getContainer();
        support.addEngineNote(ctx, container, "并行分支全部完成，继续推进");
        advanceFrom(chainNext(container), ctx.getCursor().findBranchHost(idOf(container)), ctx);
    }

    /**
     * 节点完成判定（当前任务刚终结后调用）：有 PENDING 加签任务则等待；
     * 有 PENDING 根任务时 OR=一票即成、AND=等待全员；无 PENDING 且存在 PASSED 记录即完成
     */
    private boolean nodeCompleted(EngineCtx ctx, String nodeId) {
        List<FlowTaskDO> tasks = support.loadTasks(ctx, nodeId);
        if (tasks.isEmpty()) {
            return false;
        }
        boolean hasPendingDerived = false;
        boolean hasPendingRoot = false;
        boolean hasPassed = false;
        for (FlowTaskDO task : tasks) {
            if (TaskStatus.PENDING.equals(task.getStatus())) {
                if (task.getParentTaskId() == null) {
                    hasPendingRoot = true;
                } else {
                    hasPendingDerived = true;
                }
            } else if (TaskStatus.PASSED.equals(task.getStatus())) {
                hasPassed = true;
            }
        }
        if (hasPendingDerived) {
            return false;
        }
        if (hasPendingRoot) {
            String multiMode = currentMultiMode(ctx, nodeId);
            return MULTI_OR.equals(multiMode);
        }
        return hasPassed;
    }

    private String currentMultiMode(EngineCtx ctx, String nodeId) {
        JsonNode node = ctx.getCursor().findNode(nodeId);
        return node == null ? MULTI_OR : propsOf(node).path("multiMode").asText(MULTI_OR);
    }

    /**
     * 节点的下一推进位置：链后继；分支列链尾回落到宿主容器后继（汇合点）
     */
    private JsonNode nextPositionOf(JsonNode node, FlowDesignCursor cursor) {
        JsonNode next = chainNext(node);
        if (next != null) {
            return next;
        }
        if (FlowDesignCursor.isBranchColumn(node)) {
            FlowDesignCursor.BranchHost host = cursor.findBranchHost(idOf(node));
            if (host != null) {
                return chainNext(host.getContainer());
            }
        }
        return null;
    }

    // ==================== 预览推进（无副作用 dryRun） ====================

    /**
     * 收集下一组审批节点与审批人（dryRun，无副作用）：条件按 conditions 求值（未传走默认列）；
     * 空审批人节点跳过继续；PARALLEL 各分支首组汇聚、全部分支走完才继续容器后继（与运行时汇合口径一致）；
     * CONDITIONS 单选命中分支走完即容器走完继续后继。
     *
     * @return true=该子链已走完（链尾无审批节点）；false=命中审批节点即止
     */
    private boolean dryRunCollect(JsonNode position, FlowDesignCursor cursor,
                                  Map<String, String> conditions, Long currentUserId,
                                  List<NodeApproverPreviewVO> out) {
        JsonNode node = position;
        while (node != null) {
            String type = typeOf(node);
            if (TYPE_START.equals(type) || TYPE_TRANSACT.equals(type)) {
                node = chainNext(node);
                continue;
            }
            if (TYPE_CONDITIONS.equals(type)) {
                if (FlowDesignCursor.isManualRouting(node)) {
                    // 人工路由无法自动选定分支：返回所有分支列首组审批人供参考（实际运行由审批人选择其一）
                    for (JsonNode column : branchColumns(node)) {
                        JsonNode first = chainNext(column);
                        if (first != null) {
                            dryRunCollect(first, cursor, conditions, currentUserId, out);
                        }
                    }
                    return false;
                }
                JsonNode branch = cursor.selectConditionBranch(node, conditions);
                JsonNode branchFirst = branch != null ? chainNext(branch) : null;
                if (branchFirst != null) {
                    boolean done = dryRunCollect(branchFirst, cursor, conditions, currentUserId, out);
                    if (!done) {
                        return false;
                    }
                }
                node = chainNext(node);
                continue;
            }
            if (TYPE_PARALLEL.equals(type)) {
                boolean allDone = true;
                for (JsonNode column : branchColumns(node)) {
                    JsonNode first = chainNext(column);
                    if (first != null) {
                        allDone = dryRunCollect(first, cursor, conditions, currentUserId, out) && allDone;
                    }
                }
                if (!allDone) {
                    return false;
                }
                node = chainNext(node);
                continue;
            }
            // APPROVER：解析为空（含 emptyPolicy 折算后仍空）→ 视为 AUTO_PASS 跳过继续
            Resolved resolved = resolver.resolve(node, currentUserId);
            if (resolved.getUsers().isEmpty()) {
                node = chainNext(node);
                continue;
            }
            NodeApproverPreviewVO preview = new NodeApproverPreviewVO();
            preview.setNodeId(idOf(node));
            preview.setNodeName(nameOf(node));
            preview.setMultiMode(propsOf(node).path("multiMode").asText(MULTI_OR));
            preview.setApprovers(resolved.getUsers().stream().map(user -> {
                UserBriefVO brief = new UserBriefVO();
                brief.setUserId(String.valueOf(user.getId()));
                brief.setName(FlowApproverResolver.displayName(user));
                return brief;
            }).collect(java.util.stream.Collectors.toList()));
            out.add(preview);
            return false;
        }
        return true;
    }

    // ==================== 基础设施 ====================

    /**
     * 实例级推进锁：tryLock 不等待；事务在锁内提交，锁在 finally 释放
     */
    private <T> T executeWithAdvanceLock(Long instanceId, Supplier<T> action) {
        RLock lock = redissonClient.getLock(LOCK_KEY_PREFIX + instanceId);
        boolean locked = false;
        try {
            locked = lock.tryLock(LOCK_WAIT_SECONDS, properties.getLockLeaseSeconds(), TimeUnit.SECONDS);
            if (!locked) {
                throw new BusinessException(ResultCode.PROCESS_REPEAT_SUBMIT);
            }
            return action.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ResultCode.PROCESS_REPEAT_SUBMIT);
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}
