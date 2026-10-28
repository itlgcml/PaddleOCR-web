package com.paddleocr.web.service.approval.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.paddleocr.web.common.BusinessException;
import com.paddleocr.web.common.ResultCode;
import com.paddleocr.web.mapper.entity.FlowInstanceDO;
import com.paddleocr.web.mapper.entity.FlowReturnRecordDO;
import com.paddleocr.web.mapper.entity.FlowTaskDO;
import com.paddleocr.web.mapper.entity.ProcessDO;
import com.paddleocr.web.mapper.entity.ProcessVersionDO;
import com.paddleocr.web.mapper.entity.UserDO;
import com.paddleocr.web.model.dto.TaskActionDTO;
import com.paddleocr.web.model.vo.NextApproverVO;
import com.paddleocr.web.model.vo.NodeApproverPreviewVO;
import com.paddleocr.web.model.vo.UserBriefVO;
import com.paddleocr.web.service.approval.ApprovalRuntimeProperties;
import com.paddleocr.web.service.approval.ApprovalSubProcessProperties;
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
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static com.paddleocr.web.service.approval.engine.FlowDesignCursor.TYPE_APPROVER;
import static com.paddleocr.web.service.approval.engine.FlowDesignCursor.TYPE_CONDITIONS;
import static com.paddleocr.web.service.approval.engine.FlowDesignCursor.TYPE_PARALLEL;
import static com.paddleocr.web.service.approval.engine.FlowDesignCursor.TYPE_START;
import static com.paddleocr.web.service.approval.engine.FlowDesignCursor.TYPE_SUB_PROCESS;
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
 * <p>子流程（SUB_PROCESS）：推进至节点时建子实例（同表，身份继承父实例、冗余 subDoc 与 advanceMode），
 * WAIT 模式父实例等待子实例完成后回推，IMMEDIATE 模式父实例启动即继续（旁路，父完成判定不感知子实例）；
 * 终止按整树级联（cascadeTreeTerminal 不区分模式），WAIT 子实例被拒绝时沿父链向上传播至根。
 * 整树共用 {@code approval:instance:advance:{rootInstanceId}} 树级推进锁——父子互推不再交叉取锁，
 * 规避锁环死锁。</p>
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

    /** 退回方式：逐级审批（缺省）——退回目标节点审批通过后逐级向前重审 */
    private static final String RETURN_MODE_STEP = "STEP";

    /** 退回方式：审批后直达本节点——退回目标节点审批通过后跳过中间节点，直接回到退回发起节点 */
    private static final String RETURN_MODE_DIRECT = "DIRECT";

    /** 子流程推进模式：父流程等待子实例完成后继续（缺省） */
    private static final String ADVANCE_WAIT = "WAIT";

    /** 子流程推进模式：子实例启动后父流程立即继续（旁路，父完成判定不感知子实例） */
    private static final String ADVANCE_IMMEDIATE = "IMMEDIATE";

    /** SUB_PROCESS 节点属性：内嵌子设计文档 */
    private static final String PROP_SUB_DOC = "subDoc";

    /** SUB_PROCESS 节点属性：推进模式（缺省 WAIT，兼容旧快照） */
    private static final String PROP_ADVANCE_MODE = "advanceMode";

    /** 子实例流程名分隔符（{父流程名}·{子流程节点名}） */
    private static final String SUB_NAME_SEPARATOR = "·";

    /** 预览节点归属作用域：主流程 */
    private static final String SCOPE_MAIN = "MAIN";

    /** 预览节点归属作用域：子流程（由 SUB_PROCESS 节点派生） */
    private static final String SCOPE_SUB_PROCESS = "SUB_PROCESS";

    private final FlowEngineSupport support;
    private final FlowApproverResolver resolver;
    private final RedissonClient redissonClient;
    private final ApprovalRuntimeProperties properties;
    private final ApprovalSubProcessProperties subProcessProperties;
    private final TransactionTemplate transactionTemplate;

    // ==================== 接口实现 ====================

    @Override
    public Long startInstance(ProcessDO process, ProcessVersionDO version, String bizType, Long bizId,
                              UserDO startUser, String bizTitle, String formJson,
                              List<UserDO> ccUsers, String startComment, Map<String, List<String>> startAssignees) {
        if (formJson != null && formJson.length() > properties.getSnapshotMaxLength()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "表单快照超过长度上限");
        }
        return transactionTemplate.execute(status ->
                doStartInstance(process, version, bizType, bizId, startUser, bizTitle, formJson,
                        ccUsers, startComment, startAssignees));
    }

    private Long doStartInstance(ProcessDO process, ProcessVersionDO version, String bizType, Long bizId,
                                 UserDO startUser, String bizTitle, String formJson,
                                 List<UserDO> ccUsers, String startComment, Map<String, List<String>> startAssignees) {
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
        // 根实例树身份回填（rootInstanceId=自身、depth=0）：树级锁与级联终止按此列收敛
        support.markRootInstance(instance);

        EngineCtx ctx = support.loadCtx(instance, version.getDesignJson());
        support.stageActionLog(ctx, null, ctx.getCursor().root(), TaskAction.START, startUser, startComment, null);
        support.saveCc(ctx, null, ccUsers);
        registerStartAssignees(ctx, startAssignees);
        advanceFrom(chainNext(ctx.getCursor().root()), null, ctx);
        support.flushActionLog(ctx);
        log.info("审批实例发起成功, instanceId={}, processCode={}, bizType={}, bizId={}",
                instance.getId(), process.getCode(), bizType, bizId);
        return instance.getId();
    }

    /**
     * 发起时指定审批人登记：将提交携带的 startAssignees（nodeId → userIds，目标=next-approvers
     * 预览返回的第一审批阶段节点：普通首审批节点 / 并行各列首组 / IMMEDIATE 子流程主/子双线 /
     * WAIT 穿透子 doc 首组）解析写入 ctx.assigneeOverrides，推进建任务时一次性消费（候选集校验）。
     * 子 doc 节点的覆盖由 buildSubInstance 复制到子上下文消费；父链节点由根上下文推进消费；
     * 不在首组阶段的 nodeId 不会被消费，残留随上下文丢弃（与 PASS 指定口径一致）。
     */
    private void registerStartAssignees(EngineCtx ctx, Map<String, List<String>> startAssignees) {
        if (startAssignees == null || startAssignees.isEmpty()) {
            return;
        }
        for (Map.Entry<String, List<String>> entry : startAssignees.entrySet()) {
            String nodeId = entry.getKey();
            List<String> userIds = entry.getValue();
            if (nodeId == null || nodeId.isEmpty() || userIds == null || userIds.isEmpty()) {
                continue;
            }
            List<Long> parsed = new ArrayList<>(userIds.size());
            for (String userId : userIds) {
                if (userId == null || userId.isEmpty()) {
                    continue;
                }
                try {
                    parsed.add(Long.parseLong(userId));
                } catch (NumberFormatException e) {
                    throw new BusinessException(ResultCode.TASK_RECEIVER_INVALID, "指定审批人 ID 不合法");
                }
            }
            if (!parsed.isEmpty()) {
                ctx.getAssigneeOverrides().put(nodeId, parsed);
            }
        }
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
                if (instance.getParentInstanceId() != null) {
                    // 子实例不允许独立撤销：撤销须从根实例发起，按整树级联
                    throw new BusinessException(ResultCode.SUB_INSTANCE_OPERATION_FORBIDDEN);
                }
                EngineCtx ctx = support.loadCtx(instance, null);
                // 撤销范围由单实例扩展为流程树：在途子实例（含 IMMEDIATE 旁路）一并取消
                cascadeTreeTerminal(ctx.getRootInstanceId(), InstanceStatus.CANCELED);
                support.stageActionLog(ctx, null, null, TaskAction.CANCEL, operator, comment, "撤销实例，待办任务全部取消");
                support.flushActionLog(ctx);
            });
            return null;
        });
    }

    @Override
    public NextApproverVO previewNextApprovers(Long processId, String nodeId, Long currentUserId,
                                               Map<String, String> conditions) {
        ProcessDO process = support.requirePublishedProcess(processId);
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
            dryRunCollect(start, cursor, conditions, currentUserId, SCOPE_MAIN, process.getName(), nodes);
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
        // 直达退回：刚完成的节点为 DIRECT 退回目标（含 START 重提交）时，跳过中间节点直接回到退回发起节点
        List<JsonNode> directFromNodes = consumeDirectReturn(ctx, task.getNodeId());
        for (JsonNode directFrom : directFromNodes) {
            support.addEngineNote(ctx, directFrom, "退回直达，跳过中间节点");
            resolveApproverAndCreate(directFrom, ctx);
        }
        if (!directFromNodes.isEmpty()) {
            return;
        }
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
            registerNextAssignees(node, dto.getNextAssignees(), ctx);
            registerSubProcessAssignees(next, dto.getSubProcessAssignees(), ctx);
            advanceFrom(next, ctx.getCursor().findBranchHost(idOf(node)), ctx);
        }
    }

    /**
     * 子流程（IMMEDIATE）下一节点指定审批人登记：将 PASS 携带的 subProcessAssignees（nodeId → userId，
     * 目标节点=子流程内部首节点 / 父链下一节点）写入 ctx.assigneeOverrides 供建任务时一次性消费。
     * 父链节点的覆盖在父链 advanceFrom 推进到该节点时被 resolveApproverAndCreate 消费；
     * 子流程节点的覆盖在 buildSubInstance 创建子上下文时被复制到子上下文消费。
     * 仅当下一节点为子流程且 IMMEDIATE 时生效，其余场景忽略。
     */
    private void registerSubProcessAssignees(JsonNode next, Map<String, String> subProcessAssignees, EngineCtx ctx) {
        if (subProcessAssignees == null || subProcessAssignees.isEmpty() || next == null
                || !TYPE_SUB_PROCESS.equals(typeOf(next))
                || !ADVANCE_IMMEDIATE.equals(advanceModeOf(next))) {
            return;
        }
        for (Map.Entry<String, String> entry : subProcessAssignees.entrySet()) {
            String nodeId = entry.getKey();
            String userId = entry.getValue();
            if (nodeId == null || nodeId.isEmpty() || userId == null || userId.isEmpty()) {
                continue;
            }
            long parsed;
            try {
                parsed = Long.parseLong(userId);
            } catch (NumberFormatException e) {
                throw new BusinessException(ResultCode.TASK_RECEIVER_INVALID, "指定审批人 ID 不合法");
            }
            ctx.getAssigneeOverrides().put(nodeId, Collections.singletonList(parsed));
        }
    }

    /**
     * 下一节点（普通审批节点）指定审批人登记：将 PASS 携带的 nextAssignees 写入 ctx.assigneeOverrides
     * 供建任务时一次性消费（候选逐人校验与按节点 multiMode 建任务在 resolveApproverAndCreate 消费端
     * 统一处理，与 startAssignees/routeAssignees 同口径）。目标落点=链后继穿透落点（nextApproverTarget：
     * 穿透直通办理节点 TRANSACT，分支列尾经容器汇合后继链定位），与任务详情 nextApproverOptions 下发
     * 口径一致；落点非审批节点（分支容器/子流程/链尾）时不登记，指定值静默忽略——下发侧本就为空，
     * 直调越权指向其它节点无效。分支列尾经容器汇合时由本次 PASS 触发的推进在同一操作内消费；
     * 容器尚未汇合（并行兄弟分支未走完）时下发侧不开放，登记残留随上下文丢弃（无副作用）。
     */
    private void registerNextAssignees(JsonNode node, List<String> nextAssignees, EngineCtx ctx) {
        if (nextAssignees == null || nextAssignees.isEmpty()) {
            return;
        }
        JsonNode target = ctx.getCursor().nextApproverTarget(node, null);
        if (target == null) {
            return;
        }
        List<Long> parsedIds = new ArrayList<>(nextAssignees.size());
        for (String userId : nextAssignees) {
            if (userId == null || userId.isEmpty()) {
                continue;
            }
            long parsed;
            try {
                parsed = Long.parseLong(userId);
            } catch (NumberFormatException e) {
                throw new BusinessException(ResultCode.TASK_RECEIVER_INVALID, "指定审批人 ID 不合法");
            }
            parsedIds.add(parsed);
        }
        if (!parsedIds.isEmpty()) {
            ctx.getAssigneeOverrides().put(idOf(target), parsedIds);
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
        ctx.getAssigneeOverrides().putIfAbsent(idOf(firstApprover), Collections.singletonList(parsed));
    }

    private void doReject(FlowTaskDO task, TaskActionDTO dto, UserDO operator, EngineCtx ctx, JsonNode node) {
        requireComment(dto, "拒绝");
        support.requireButtonEnabled(node, "REJECT");
        support.finishTask(task, TaskStatus.REJECTED, TaskAction.REJECT, dto.getComment());
        // 与 RETURN 同口径：实例内全部剩余 PENDING 级联取消（拒绝退回/终止均不留并行其它分支残留）
        support.cancelInstancePendingTasks(ctx.getInstance().getId());
        support.stageActionLog(ctx, task, node, TaskAction.REJECT, operator, dto.getComment(), null);

        JsonNode props = node == null ? null : propsOf(node);
        boolean backToStart = props != null && props.path("canRejectToStart").asBoolean(false);
        String rejectTargetId = props == null ? "" : props.path("rejectTargetId").asText("");
        if (backToStart || (rejectTargetId != null && !rejectTargetId.isEmpty())) {
            // 重来前清理在跑子实例：回到子流程之前节点时，等待中/旁路的子实例一并取消
            cancelRunningSubInstances(ctx);
            // 拒绝退回按逐级语义推进，未消费直达记录一并重置（新退回由 doReturn 重新记录）
            support.resetPendingReturns(ctx.getInstance().getId());
        }
        if (backToStart) {
            backToStart(ctx, "拒绝退回，发起人重新提交");
        } else if (rejectTargetId != null && !rejectTargetId.isEmpty()) {
            JsonNode target = requireReturnTarget(ctx, rejectTargetId, task.getNodeId());
            backToNode(ctx, target, "拒绝退回到【" + nameOf(target) + "】");
        } else if (isSubInstance(ctx)) {
            // 子实例拒绝终止：WAIT 向上传播终止整树，IMMEDIATE 仅终止自身子树
            rejectSubInstance(ctx);
        } else {
            support.finishInstance(ctx, InstanceStatus.REJECTED);
        }
    }

    /**
     * 退回：目标节点（含 START=退回发起人）由退回人指定；returnAssignees 指定目标节点审批人
     * （目标为审批节点且候选非空时必填，登记一次性覆盖由目标任务消费）；returnMode 二选一——
     * STEP（缺省）目标节点审批通过后逐级向前重审；DIRECT 目标节点审批通过（含 START 重提交、
     * 空审批人自动通过）后跳过中间节点直达本节点（returnContext 记录待消费上下文）。
     * 退回是「回到过去」：实例内其余全部 PENDING 任务一并级联取消（并行其它分支/未达节点不留残留）。
     */
    private void doReturn(FlowTaskDO task, TaskActionDTO dto, UserDO operator, EngineCtx ctx, JsonNode node) {
        requireButtonEnabledReturn(node);
        if (dto.getTargetNodeId() == null || dto.getTargetNodeId().isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "退回目标节点不能为空");
        }
        requireComment(dto, "退回");
        String returnMode = dto.getReturnMode() == null || dto.getReturnMode().isEmpty()
                ? RETURN_MODE_STEP : dto.getReturnMode();
        JsonNode target = requireReturnTarget(ctx, dto.getTargetNodeId(), task.getNodeId());
        support.finishTask(task, TaskStatus.REJECTED, TaskAction.RETURN, dto.getComment());
        // 实例内全部剩余 PENDING 级联取消（含本节点或签/加签链、并行其它分支、更下游节点）
        support.cancelInstancePendingTasks(ctx.getInstance().getId());
        String modeLabel = RETURN_MODE_DIRECT.equals(returnMode) ? "审批后直达本节点" : "逐级审批";
        support.stageActionLog(ctx, task, node, TaskAction.RETURN, operator, dto.getComment(),
                "退回到【" + nameOf(target) + "】（" + modeLabel + "）");
        // 重来前清理在跑子实例：退回到子流程之前节点时，等待中/旁路的子实例一并取消
        cancelRunningSubInstances(ctx);
        // 退回记录落库（flow_return_record 按次插入互不覆盖）：DIRECT 记 PENDING 待目标节点完成
        // 消费直达（接力退回嵌套直达意图完整保留）；STEP 记 FINISHED 纯审计留痕。
        // 不重置历史 PENDING——直达意图按次生效，拒绝退回（doReject）与实例终态才重置
        support.insertReturnRecord(ctx, task.getId(), task.getNodeId(), dto.getTargetNodeId(), returnMode,
                RETURN_MODE_DIRECT.equals(returnMode)
                        ? FlowReturnRecordDO.STATUS_PENDING : FlowReturnRecordDO.STATUS_FINISHED);
        registerReturnAssignees(target, dto.getReturnAssignees(), ctx);
        backToNode(ctx, target, null);
    }

    /**
     * 退回指定审批人登记：目标为审批节点时按节点候选集校验——候选非空则 returnAssignees 必填
     * （40069），解析写入 ctx.assigneeOverrides（建任务时逐人候选校验 40057 兜底）；候选为空
     * 不可指定（节点按 emptyPolicy 自动通过）；目标为 START 时忽略（发起人重提交任务固定）。
     */
    private void registerReturnAssignees(JsonNode target, List<String> returnAssignees, EngineCtx ctx) {
        if (TYPE_START.equals(typeOf(target))) {
            return;
        }
        List<String> assignees = returnAssignees == null ? Collections.emptyList() : returnAssignees;
        Resolved resolved = resolver.resolve(target, ctx.getInstance().getStartUserId());
        if (resolved.getUsers().isEmpty()) {
            if (!assignees.isEmpty()) {
                throw new BusinessException(ResultCode.TASK_RECEIVER_INVALID,
                        "退回目标节点无可用审批人，不支持指定审批人");
            }
            return;
        }
        if (assignees.isEmpty()) {
            throw new BusinessException(ResultCode.TASK_RETURN_ASSIGNEE_REQUIRED);
        }
        List<Long> parsed = new ArrayList<>(assignees.size());
        for (String userId : assignees) {
            try {
                parsed.add(Long.parseLong(userId));
            } catch (NumberFormatException e) {
                throw new BusinessException(ResultCode.TASK_RECEIVER_INVALID, "指定审批人 ID 不合法");
            }
        }
        ctx.getAssigneeOverrides().put(idOf(target), parsed);
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
     * 退回目标校验：必须位于当前节点先序路径之前的 START/APPROVER，或实例内已办结且位于
     * 上游分支列内的 APPROVER（与 returnTargets 下发口径一致，API 直调不可指向并行其它
     * 分支/后续节点/容器）→ 否则 40056；fromNodeId 缺失时退化为存在性校验
     */
    private JsonNode requireReturnTarget(EngineCtx ctx, String targetNodeId, String fromNodeId) {
        JsonNode target = null;
        if (fromNodeId != null && !fromNodeId.isEmpty()) {
            Set<String> passedNodeIds = support.passedNodeIds(ctx.getInstance().getId());
            for (JsonNode candidate : ctx.getCursor().rejectTargetsBefore(fromNodeId, passedNodeIds)) {
                if (targetNodeId.equals(idOf(candidate))) {
                    target = candidate;
                    break;
                }
            }
        } else {
            target = ctx.getCursor().findNode(targetNodeId);
        }
        if (target == null) {
            throw new BusinessException(ResultCode.TASK_RETURN_TARGET_INVALID);
        }
        return target;
    }

    /**
     * 直达退回消费：实例内 status=PENDING 且 target=completedNodeId 的直达记录逐条置 CONSUMED，
     * 返回各自退回发起节点（调用方在其上重建任务=直达）。记录按次落库互不覆盖——接力退回
     * （a1→X 后 X→Y）在 Y、X 依次完成时各自直达，嵌套意图完整保留；本期退回级联取消全实例
     * PENDING，在途记录至多一条，列表口径为并行各自退回（预留）天然兼容。
     * 记录指向节点不在快照时跳过该条（数据异常不阻塞推进）。
     */
    private List<JsonNode> consumeDirectReturn(EngineCtx ctx, String completedNodeId) {
        List<FlowReturnRecordDO> records = support.pendingDirectReturns(ctx.getInstance().getId(), completedNodeId);
        if (records.isEmpty()) {
            return Collections.emptyList();
        }
        List<JsonNode> fromNodes = new ArrayList<>(records.size());
        for (FlowReturnRecordDO record : records) {
            support.markReturnConsumed(record);
            JsonNode from = ctx.getCursor().findNode(record.getFromNodeId());
            if (from != null) {
                fromNodes.add(from);
            }
        }
        return fromNodes;
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
            if (TYPE_SUB_PROCESS.equals(type)) {
                // 建子实例并推进子 doc（同一树锁 + 同一事务内完成）
                buildSubInstance(ctx, node);
                if (ADVANCE_WAIT.equals(advanceModeOf(node))) {
                    // 父实例在节点处等待子实例完成（无 PENDING 任务，由子实例完成时回推）
                    return;
                }
                // IMMEDIATE：子实例已启动，父流程按旁路语义继续向后推进
                node = chainNext(node);
                continue;
            }
            // APPROVER
            resolveApproverAndCreate(node, ctx);
            return;
        }
        if (host != null) {
            tryMergeParallel(host, ctx);
            return;
        }
        if (isSubInstance(ctx)) {
            // 子实例主干根链走完：置 COMPLETED，WAIT 子实例回推父流程
            completeSubInstance(ctx);
            return;
        }
        support.finishInstance(ctx, InstanceStatus.COMPLETED);
    }

    /**
     * 审批节点处理：解析审批人；空则按 emptyPolicy 兜底（说明记 ENGINE 日志，AUTO_PASS 落伪任务并继续推进）。
     * 指定审批人（ctx.assigneeOverrides：发起 startAssignees / 人工路由 routeAssignees /
     * 子流程 subProcessAssignees 登记）优先于默认解析：每人须为该节点候选审批人之一，
     * 命中后仅为其创建任务（或签单人选 1、会签按选择人数）并按节点 multiMode 判定，记 ENGINE 日志。
     */
    private void resolveApproverAndCreate(JsonNode node, EngineCtx ctx) {
        Resolved resolved = resolver.resolve(node, ctx.getInstance().getStartUserId());
        for (String note : resolved.getEngineNotes()) {
            support.addEngineNote(ctx, node, note);
        }
        List<Long> overrideUserIds = ctx.getAssigneeOverrides().remove(idOf(node));
        if (overrideUserIds != null && !overrideUserIds.isEmpty()) {
            List<UserDO> assigned = new ArrayList<>(overrideUserIds.size());
            for (Long overrideUserId : overrideUserIds) {
                UserDO user = resolved.getUsers().stream()
                        .filter(item -> overrideUserId.equals(item.getId()))
                        .findFirst()
                        .orElseThrow(() -> new BusinessException(ResultCode.TASK_RECEIVER_INVALID,
                                "指定审批人不在该节点的候选审批人范围内"));
                if (assigned.stream().noneMatch(item -> item.getId().equals(user.getId()))) {
                    assigned.add(user);
                }
            }
            String multiMode = propsOf(node).path("multiMode").asText(MULTI_OR);
            String names = assigned.stream().map(FlowApproverResolver::displayName)
                    .collect(Collectors.joining("、"));
            support.addEngineNote(ctx, node, "指定审批人：" + names);
            support.createTasks(ctx, node, assigned, MULTI_AND.equals(multiMode) ? MULTI_AND : MULTI_OR);
            return;
        }
        if (!resolved.getUsers().isEmpty()) {
            String multiMode = propsOf(node).path("multiMode").asText(MULTI_OR);
            support.createTasks(ctx, node, resolved.getUsers(), MULTI_AND.equals(multiMode) ? MULTI_AND : MULTI_OR);
            return;
        }
        support.addEngineNote(ctx, node, "节点无可用审批人，自动通过");
        support.createAutoPassedTask(ctx, node);
        // 直达退回：目标节点空审批人自动通过即视为节点完成，直接回到退回发起节点
        List<JsonNode> directFromNodes = consumeDirectReturn(ctx, idOf(node));
        for (JsonNode directFrom : directFromNodes) {
            support.addEngineNote(ctx, directFrom, "退回直达，跳过中间节点");
            resolveApproverAndCreate(directFrom, ctx);
        }
        if (!directFromNodes.isEmpty()) {
            return;
        }
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

    // ==================== 子流程（SUB_PROCESS） ====================

    /**
     * 节点推进模式：仅 WAIT/IMMEDIATE 两种（缺省 WAIT，旧快照与非法值一律按 WAIT 处理）
     */
    private String advanceModeOf(JsonNode node) {
        return ADVANCE_IMMEDIATE.equals(propsOf(node).path(PROP_ADVANCE_MODE).asText(ADVANCE_WAIT))
                ? ADVANCE_IMMEDIATE : ADVANCE_WAIT;
    }

    /**
     * 是否子实例上下文（parentInstanceId 非空 = 由 SUB_PROCESS 节点派生）
     */
    private boolean isSubInstance(EngineCtx ctx) {
        return ctx.getParentInstanceId() != null;
    }

    /**
     * 子设计文档校验：SUB_PROCESS.props.subDoc 必须为含 root 且 root.type=START 的 WorkflowDoc
     */
    private JsonNode requireSubDoc(JsonNode subProcessNode) {
        JsonNode subDoc = propsOf(subProcessNode).path(PROP_SUB_DOC);
        if (!subDoc.isObject() || !TYPE_START.equals(typeOf(subDoc.path("root")))) {
            throw new BusinessException(ResultCode.SUB_PROCESS_DESIGN_INVALID,
                    "子流程【" + nameOf(subProcessNode) + "】设计缺失或根节点非法");
        }
        return subDoc;
    }

    @Override
    public void buildSubInstance(EngineCtx parentCtx, JsonNode subProcessNode) {
        JsonNode subDoc = requireSubDoc(subProcessNode);
        int parentDepth = parentCtx.getDepth() == null ? 0 : parentCtx.getDepth();
        if (parentDepth + 1 > subProcessProperties.getMaxDepth()) {
            throw new BusinessException(ResultCode.SUB_PROCESS_DEPTH_EXCEEDED);
        }
        String advanceMode = advanceModeOf(subProcessNode);
        FlowInstanceDO parent = parentCtx.getInstance();
        FlowInstanceDO sub = new FlowInstanceDO();
        // 身份继承父实例（同一单据、同一发起人、同一版本快照）
        sub.setProcessId(parent.getProcessId());
        sub.setProcessCode(parent.getProcessCode());
        sub.setProcessName(parent.getProcessName() + SUB_NAME_SEPARATOR + nameOf(subProcessNode));
        sub.setProcessVersion(parent.getProcessVersion());
        sub.setProcessVersionId(parent.getProcessVersionId());
        sub.setBizType(parent.getBizType());
        sub.setBizId(parent.getBizId());
        sub.setBizTitle(parent.getBizTitle());
        sub.setFormSnapshot(parent.getFormSnapshot());
        sub.setStartUserId(parent.getStartUserId());
        sub.setStartUserName(parent.getStartUserName());
        sub.setParentInstanceId(parent.getId());
        sub.setParentNodeId(idOf(subProcessNode));
        sub.setRootInstanceId(parentCtx.getRootInstanceId());
        sub.setDepth(parentDepth + 1);
        sub.setDesignJson(support.writeJson(subDoc));
        sub.setAdvanceMode(advanceMode);
        sub.setStatus(InstanceStatus.RUNNING);
        support.insertInstance(sub);

        EngineCtx subCtx = support.loadCtx(sub, sub.getDesignJson());
        // 子流程节点指定审批人：父上下文中属于子 doc 的覆盖复制到子上下文（nodeId 全局唯一，逐节点定位）
        for (Map.Entry<String, List<Long>> entry : parentCtx.getAssigneeOverrides().entrySet()) {
            if (subCtx.getCursor().findNode(entry.getKey()) != null) {
                subCtx.getAssigneeOverrides().put(entry.getKey(), entry.getValue());
            }
        }
        support.stageActionLog(subCtx, null, subCtx.getCursor().root(), TaskAction.START, null, null, "子流程启动");
        support.addEngineNote(parentCtx, subProcessNode, ADVANCE_WAIT.equals(advanceMode)
                ? "进入子流程【" + nameOf(subProcessNode) + "】，等待其完成"
                : "进入子流程【" + nameOf(subProcessNode) + "】，启动后继续");
        // 子 doc 首节点推进（空审批人/条件求值/降级等既有语义原样作用于子 doc）；
        // 子 doc 无审批节点时链尾判定直接触发 completeSubInstance（WAIT 同步回推父，此处不再重复推进）
        advanceFrom(chainNext(subCtx.getCursor().root()), null, subCtx);
        support.flushActionLog(subCtx);
        log.info("子流程实例创建, parentInstanceId={}, subInstanceId={}, nodeId={}, advanceMode={}, depth={}",
                parent.getId(), sub.getId(), idOf(subProcessNode), advanceMode, sub.getDepth());
    }

    @Override
    public void completeSubInstance(EngineCtx subCtx) {
        FlowInstanceDO sub = subCtx.getInstance();
        support.finishInstance(subCtx, InstanceStatus.COMPLETED);
        boolean waitMode = !ADVANCE_IMMEDIATE.equals(sub.getAdvanceMode());
        support.addEngineNote(subCtx, null, waitMode ? "子流程已完成，父流程继续" : "子流程已完成（旁路）");
        if (!waitMode) {
            // IMMEDIATE：旁路自终态，不回推父流程（父可能仍在后续节点流转或已终态）
            return;
        }
        FlowInstanceDO parent = support.requireInstance(sub.getParentInstanceId());
        if (!InstanceStatus.RUNNING.equals(parent.getStatus())) {
            support.addEngineNote(subCtx, null, "父流程已终止，子流程完成不再回推");
            return;
        }
        EngineCtx parentCtx = support.loadCtx(parent, null);
        JsonNode parentNode = parentCtx.getCursor().findNode(sub.getParentNodeId());
        if (parentNode == null) {
            throw new BusinessException(ResultCode.PROCESS_NODE_NOT_FOUND, "父流程中的子流程节点缺失");
        }
        // 子实例侧操作触发本次链尾时，父上下文无宿主操作记录：父侧推进说明并入子实例本次记录
        boolean borrowNotes = parentCtx.getPendingActionLog() == null;
        advanceFrom(chainNext(parentNode), parentCtx.getCursor().findBranchHost(idOf(parentNode)), parentCtx);
        if (borrowNotes) {
            subCtx.getEngineNotes().addAll(parentCtx.getEngineNotes());
            parentCtx.getEngineNotes().clear();
        }
        log.info("子流程实例完成并回推, subInstanceId={}, parentInstanceId={}, parentNodeId={}",
                sub.getId(), parent.getId(), sub.getParentNodeId());
    }

    @Override
    public void cascadeTreeTerminal(Long rootInstanceId, String targetStatus) {
        for (FlowInstanceDO instance : support.loadRunningTreeInstances(rootInstanceId)) {
            int canceled = support.cancelInstancePendingTasks(instance.getId());
            support.finishInstance(instance, targetStatus);
            log.info("流程树级联终止, instanceId={}, status={}, canceledTasks={}",
                    instance.getId(), targetStatus, canceled);
        }
    }

    /**
     * 子树级联终止：树内属于 ancestorId 子树的非终态实例置 targetStatus，PENDING 任务级联取消。
     * includeSelf=true 用于 IMMEDIATE 子实例拒绝（自身及下层子树终止，父流程与旁路子实例不受影响）；
     * includeSelf=false 用于退回清理（仅清理派生出的在跑子实例，当前实例继续流转）
     */
    private void cascadeSubTree(Long rootInstanceId, Long ancestorId, String targetStatus, boolean includeSelf) {
        List<FlowInstanceDO> running = support.loadRunningTreeInstances(rootInstanceId);
        Map<Long, Long> parentOf = new HashMap<>(running.size() * 4 / 3 + 1);
        for (FlowInstanceDO instance : running) {
            parentOf.put(instance.getId(), instance.getParentInstanceId());
        }
        for (FlowInstanceDO instance : running) {
            if (!inSubTree(instance.getId(), ancestorId, parentOf, includeSelf)) {
                continue;
            }
            int canceled = support.cancelInstancePendingTasks(instance.getId());
            support.finishInstance(instance, targetStatus);
            log.info("流程子树级联终止, instanceId={}, ancestorId={}, status={}, canceledTasks={}",
                    instance.getId(), ancestorId, targetStatus, canceled);
        }
    }

    /**
     * 实例是否位于 ancestorId 子树内（沿 parentInstanceId 链回溯；自身是否计入由 includeSelf 决定）
     */
    private boolean inSubTree(Long instanceId, Long ancestorId, Map<Long, Long> parentOf, boolean includeSelf) {
        if (instanceId.equals(ancestorId)) {
            return includeSelf;
        }
        Long cursor = parentOf.get(instanceId);
        while (cursor != null) {
            if (cursor.equals(ancestorId)) {
                return true;
            }
            cursor = parentOf.get(cursor);
        }
        return false;
    }

    /**
     * 退回/驳回重来前清理在跑子实例：当前实例派生出的在跑子实例连同其下层子树一并取消，
     * 避免父流程退回后仍有等待中/旁路的子实例继续流转（§4.4.4）
     */
    private void cancelRunningSubInstances(EngineCtx ctx) {
        cascadeSubTree(ctx.getRootInstanceId(), ctx.getInstance().getId(), InstanceStatus.CANCELED, false);
    }

    /**
     * 子实例拒绝终止：WAIT 子实例沿父链向上传播（整树终止，父流程一并终止）；
     * IMMEDIATE 子实例仅自身子树终止（旁路，父流程不受影响）
     */
    private void rejectSubInstance(EngineCtx ctx) {
        FlowInstanceDO sub = ctx.getInstance();
        if (ADVANCE_IMMEDIATE.equals(sub.getAdvanceMode())) {
            support.addEngineNote(ctx, null, "子流程被拒绝（旁路，父流程不受影响）");
            cascadeSubTree(ctx.getRootInstanceId(), sub.getId(), InstanceStatus.REJECTED, true);
            return;
        }
        support.addEngineNote(ctx, null, "子流程被拒绝，父流程已终止");
        cascadeTreeTerminal(ctx.getRootInstanceId(), InstanceStatus.REJECTED);
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
                                  String scopeType, String scopeName,
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
                            dryRunCollect(first, cursor, conditions, currentUserId, scopeType, scopeName, out);
                        }
                    }
                    return false;
                }
                JsonNode branch = cursor.selectConditionBranch(node, conditions);
                JsonNode branchFirst = branch != null ? chainNext(branch) : null;
                if (branchFirst != null) {
                    boolean done = dryRunCollect(branchFirst, cursor, conditions, currentUserId, scopeType, scopeName, out);
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
                        allDone = dryRunCollect(first, cursor, conditions, currentUserId, scopeType, scopeName, out) && allDone;
                    }
                }
                if (!allDone) {
                    return false;
                }
                node = chainNext(node);
                continue;
            }
            if (TYPE_SUB_PROCESS.equals(type)) {
                // 穿透语义（§4.4.7）：WAIT 节点进入 subDoc 取首个审批节点（子链走完则父流程继续 childNode）；
                // IMMEDIATE 节点子实例旁路启动——其内部首组审批人同样计入下一组，父流程立即继续 childNode 模拟，
                // 两路并行推进，与运行态（buildSubInstance 建子实例 + 继续 childNode）路径一致
                JsonNode subDoc = propsOf(node).path(PROP_SUB_DOC);
                boolean subDocValid = subDoc.isObject() && TYPE_START.equals(typeOf(subDoc.path("root")));
                if (subDocValid) {
                    FlowDesignCursor subCursor = FlowDesignCursor.of(subDoc);
                    boolean subDone = dryRunCollect(chainNext(subCursor.root()), subCursor, conditions,
                            currentUserId, SCOPE_SUB_PROCESS, nameOf(node), out);
                    if (ADVANCE_WAIT.equals(advanceModeOf(node)) && !subDone) {
                        // WAIT：父实例等待子实例，子链命中审批节点即止；子链走完才继续父 childNode
                        return false;
                    }
                    // IMMEDIATE：子链首组审批人已计入下一组（旁路，走完与否不影响父链），继续沿父链模拟
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
            preview.setScopeType(scopeType);
            preview.setScopeName(scopeName);
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
     * 解析实例所属流程树的根实例 ID（旧数据 rootInstanceId 未回填时回退自身）
     */
    private Long resolveRootInstanceId(Long instanceId) {
        Long root = support.requireInstance(instanceId).getRootInstanceId();
        return root == null || root.compareTo(0L) <= 0 ? instanceId : root;
    }

    /**
     * 树级推进锁：按实例解析 rootInstanceId 加锁（整树串行），tryLock 不等待；
     * 事务在锁内提交，锁在 finally 释放。父子互推（建子实例/子实例回推）共用同一把锁，
     * 不再交叉获取两级实例锁——规避"父→子、子→父"锁环死锁
     */
    private <T> T executeWithAdvanceLock(Long instanceId, Supplier<T> action) {
        RLock lock = redissonClient.getLock(LOCK_KEY_PREFIX + resolveRootInstanceId(instanceId));
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
