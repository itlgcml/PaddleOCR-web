package com.paddleocr.web.service.approval.engine;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paddleocr.web.common.BusinessException;
import com.paddleocr.web.common.ResultCode;
import com.paddleocr.web.mapper.FlowCcMapper;
import com.paddleocr.web.mapper.FlowInstanceMapper;
import com.paddleocr.web.mapper.FlowTaskLogMapper;
import com.paddleocr.web.mapper.FlowTaskMapper;
import com.paddleocr.web.mapper.ProcessMapper;
import com.paddleocr.web.mapper.ProcessVersionMapper;
import com.paddleocr.web.mapper.UserMapper;
import com.paddleocr.web.mapper.entity.FlowCcDO;
import com.paddleocr.web.mapper.entity.FlowInstanceDO;
import com.paddleocr.web.mapper.entity.FlowTaskDO;
import com.paddleocr.web.mapper.entity.FlowTaskLogDO;
import com.paddleocr.web.mapper.entity.ProcessDO;
import com.paddleocr.web.mapper.entity.ProcessVersionDO;
import com.paddleocr.web.mapper.entity.UserDO;
import com.paddleocr.web.model.vo.ApproverButtonVO;
import com.paddleocr.web.service.approval.InstanceStatus;
import com.paddleocr.web.service.approval.ProcessStatus;
import com.paddleocr.web.service.approval.TaskAction;
import com.paddleocr.web.service.approval.TaskStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.paddleocr.web.service.approval.engine.FlowDesignCursor.TYPE_APPROVER;
import static com.paddleocr.web.service.approval.engine.FlowDesignCursor.idOf;
import static com.paddleocr.web.service.approval.engine.FlowDesignCursor.nameOf;
import static com.paddleocr.web.service.approval.engine.FlowDesignCursor.propsOf;
import static com.paddleocr.web.service.approval.engine.FlowDesignCursor.typeOf;

/**
 * 引擎持久化与校验支撑：任务/日志/抄送落库、实例终态、运行前提校验、上下文装配
 *
 * @author paddleocr
 * @date 2026-10-12
 */
@Component
@RequiredArgsConstructor
public class FlowEngineSupport {

    /** AUTO_PASS 伪任务的系统执行人 */
    private static final long SYSTEM_USER_ID = 0L;
    private static final String SYSTEM_USER_NAME = "系统自动";

    private static final String SIGN_NORMAL = "NORMAL";

    private final FlowInstanceMapper flowInstanceMapper;
    private final FlowTaskMapper flowTaskMapper;
    private final FlowTaskLogMapper flowTaskLogMapper;
    private final FlowCcMapper flowCcMapper;
    private final ProcessMapper processMapper;
    private final ProcessVersionMapper processVersionMapper;
    private final UserMapper userMapper;
    private final ObjectMapper objectMapper;

    // ==================== 上下文装配 ====================

    /**
     * 装配推进上下文：designJson 为空时按实例冻结的 processVersionId 加载版本快照
     */
    public EngineCtx loadCtx(FlowInstanceDO instance, String designJson) {
        if (designJson == null) {
            ProcessVersionDO version = flowProcessVersion(instance.getProcessVersionId());
            designJson = version.getDesignJson();
        }
        return new EngineCtx(instance, parseCursor(designJson), parseFormValues(instance.getFormSnapshot()));
    }

    private ProcessVersionDO flowProcessVersion(Long versionId) {
        ProcessVersionDO version = processVersionMapper.selectById(versionId);
        if (version == null) {
            throw new BusinessException(ResultCode.INSTANCE_NOT_FOUND, "实例版本快照缺失");
        }
        return version;
    }

    /**
     * 解析 WorkflowDoc JSON 为游标（解析失败视为数据异常，实例不可继续推进）
     */
    public FlowDesignCursor parseCursor(String designJson) {
        try {
            return FlowDesignCursor.of(objectMapper.readTree(designJson));
        } catch (Exception e) {
            throw new IllegalStateException("流程设计快照解析失败", e);
        }
    }

    /**
     * 快照解析（展示降级口径）：空/解析失败返回 null，不影响详情其余部分展示
     */
    public FlowDesignCursor parseCursorQuietly(String designJson) {
        if (designJson == null || designJson.isEmpty()) {
            return null;
        }
        try {
            return FlowDesignCursor.of(objectMapper.readTree(designJson));
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 版本快照 design_json 原文（详情/办理视图画布渲染用）；版本缺失返回 null
     */
    public String loadVersionDesignJson(Long versionId) {
        ProcessVersionDO version = processVersionMapper.selectById(versionId);
        return version == null ? null : version.getDesignJson();
    }

    /**
     * 表单快照 JSON → 平面值 Map（值统一 asText，条件求值做数值-字符串自适应）
     */
    public Map<String, String> parseFormValues(String formJson) {
        if (formJson == null || formJson.isEmpty()) {
            return new HashMap<>();
        }
        try {
            JsonNode tree = objectMapper.readTree(formJson);
            Map<String, String> values = new LinkedHashMap<>();
            Iterator<Map.Entry<String, JsonNode>> fields = tree.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                JsonNode value = entry.getValue();
                values.put(entry.getKey(), value == null || value.isNull() ? null : value.asText());
            }
            return values;
        } catch (Exception e) {
            throw new IllegalStateException("表单快照解析失败", e);
        }
    }

    // ==================== 运行前提校验 ====================

    /**
     * 流程定义（PUBLISHED）校验：缺失/未发布 → 40046
     */
    public ProcessDO requirePublishedProcess(Long processId) {
        ProcessDO process = processMapper.selectById(processId);
        if (process == null || !ProcessStatus.PUBLISHED.equals(process.getStatus())) {
            throw new BusinessException(ResultCode.PROCESS_NOT_PUBLISHED);
        }
        return process;
    }

    /**
     * 流程当前发布版本快照校验：缺失/未发布 → 40046
     */
    public ProcessVersionDO requirePublishedVersion(Long processId) {
        ProcessDO process = requirePublishedProcess(processId);
        ProcessVersionDO version = processVersionMapper.selectOne(new LambdaQueryWrapper<ProcessVersionDO>()
                .eq(ProcessVersionDO::getProcessId, processId)
                .eq(ProcessVersionDO::getVersion, process.getCurrentVersion())
                .last("LIMIT 1"));
        if (version == null) {
            throw new BusinessException(ResultCode.PROCESS_NOT_PUBLISHED);
        }
        return version;
    }

    /**
     * 实例校验：查无/已删 → 40051
     */
    public FlowInstanceDO requireInstance(Long instanceId) {
        FlowInstanceDO instance = flowInstanceMapper.selectById(instanceId);
        if (instance == null) {
            throw new BusinessException(ResultCode.INSTANCE_NOT_FOUND);
        }
        return instance;
    }

    /**
     * 任务校验：查无/已删 → 40053
     */
    public FlowTaskDO requireTask(Long taskId) {
        FlowTaskDO task = flowTaskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException(ResultCode.TASK_NOT_FOUND);
        }
        return task;
    }

    /**
     * 目标用户校验：ID 非法/查无/停用 → 40057
     */
    public UserDO requireUser(String userId) {
        Long id;
        try {
            id = Long.parseLong(userId);
        } catch (NumberFormatException e) {
            throw new BusinessException(ResultCode.TASK_RECEIVER_INVALID);
        }
        UserDO user = id.compareTo(0L) > 0 ? userMapper.selectById(id) : null;
        if (user == null || !Integer.valueOf(1).equals(user.getStatus())) {
            throw new BusinessException(ResultCode.TASK_RECEIVER_INVALID);
        }
        return user;
    }

    /**
     * 按钮启用校验：节点配置 disabled → 40058（COMMENT/COPY 恒可用，不走本方法）
     */
    public void requireButtonEnabled(JsonNode node, String buttonKey) {
        if (isButtonDisabled(node, buttonKey)) {
            throw new BusinessException(ResultCode.TASK_ACTION_DISABLED);
        }
    }

    /**
     * 按钮是否被禁用：非 APPROVER 节点/未配置该按钮视为启用（与 requireButtonEnabled 同口径）
     */
    public boolean isButtonDisabled(JsonNode node, String buttonKey) {
        if (node == null || !TYPE_APPROVER.equals(typeOf(node))) {
            return false;
        }
        JsonNode buttons = propsOf(node).path("buttons");
        if (!buttons.isArray()) {
            return false;
        }
        for (JsonNode button : buttons) {
            if (buttonKey.equals(button.path("key").asText())) {
                return !button.path("enabled").asBoolean(true);
            }
        }
        return false;
    }

    /**
     * 节点按钮配置快照透传（ApproverProps.buttons → VO；非 APPROVER 节点返回空数组）
     */
    public List<ApproverButtonVO> readButtons(JsonNode node) {
        List<ApproverButtonVO> result = new ArrayList<>();
        if (node == null || !TYPE_APPROVER.equals(typeOf(node))) {
            return result;
        }
        JsonNode buttons = propsOf(node).path("buttons");
        if (!buttons.isArray()) {
            return result;
        }
        for (JsonNode button : buttons) {
            ApproverButtonVO vo = new ApproverButtonVO();
            vo.setKey(button.path("key").asText());
            vo.setLabel(button.path("label").asText());
            vo.setEnabled(button.path("enabled").asBoolean(true));
            result.add(vo);
        }
        return result;
    }

    // ==================== 任务落库 ====================

    /**
     * 落实例（发起）
     */
    public void insertInstance(FlowInstanceDO instance) {
        flowInstanceMapper.insert(instance);
    }

    /**
     * 实例全部 PENDING 任务（撤销级联用）
     */
    public List<FlowTaskDO> loadPendingTasks(Long instanceId) {
        return flowTaskMapper.selectList(new LambdaQueryWrapper<FlowTaskDO>()
                .eq(FlowTaskDO::getInstanceId, instanceId)
                .eq(FlowTaskDO::getStatus, TaskStatus.PENDING));
    }

    /**
     * 按 ID 查启用用户（退回发起人生成重提交任务；发起人在发起后停用时抛 40048 语义由调用方兜底）
     */
    public UserDO requireUserById(Long userId) {
        UserDO user = userMapper.selectById(userId);
        if (user == null || !Integer.valueOf(1).equals(user.getStatus())) {
            throw new BusinessException(ResultCode.TASK_RECEIVER_INVALID, "发起人不可用");
        }
        return user;
    }

    /**
     * 生成一组任务（会签每人一条根任务，或签同构）
     */
    public List<FlowTaskDO> createTasks(EngineCtx ctx, JsonNode node, List<UserDO> users, String multiMode) {
        List<FlowTaskDO> tasks = new ArrayList<>(users.size());
        for (UserDO user : users) {
            tasks.add(newTask(ctx, node, user, multiMode, SIGN_NORMAL, null));
        }
        return tasks;
    }

    /**
     * 生成单人任务（普通/加签/转办/重提交共用）
     */
    public FlowTaskDO newTask(EngineCtx ctx, JsonNode node, UserDO assignee, String multiMode,
                              String signType, Long parentTaskId) {
        FlowTaskDO task = new FlowTaskDO();
        task.setInstanceId(ctx.getInstance().getId());
        task.setNodeId(idOf(node));
        task.setNodeName(nameOf(node));
        task.setAssigneeId(assignee.getId());
        task.setAssigneeName(FlowApproverResolver.displayName(assignee));
        task.setMultiMode(multiMode);
        task.setSignType(signType);
        task.setParentTaskId(parentTaskId);
        task.setStatus(TaskStatus.PENDING);
        flowTaskMapper.insert(task);
        return task;
    }

    /**
     * AUTO_PASS 伪任务：PASSED + 系统执行人（并行汇合与进度判定口径统一）
     */
    public void createAutoPassedTask(EngineCtx ctx, JsonNode node) {
        FlowTaskDO task = new FlowTaskDO();
        task.setInstanceId(ctx.getInstance().getId());
        task.setNodeId(idOf(node));
        task.setNodeName(nameOf(node));
        task.setAssigneeId(SYSTEM_USER_ID);
        task.setAssigneeName(SYSTEM_USER_NAME);
        task.setMultiMode("OR");
        task.setSignType(SIGN_NORMAL);
        task.setStatus(TaskStatus.PASSED);
        task.setAction(TaskAction.AUTO_PASS);
        task.setFinishTime(LocalDateTime.now());
        flowTaskMapper.insert(task);
    }

    /**
     * 任务终态更新
     */
    public void finishTask(FlowTaskDO task, String status, String action, String comment) {
        FlowTaskDO update = new FlowTaskDO();
        update.setId(task.getId());
        update.setStatus(status);
        update.setAction(action);
        update.setComment(comment);
        update.setFinishTime(LocalDateTime.now());
        flowTaskMapper.updateById(update);
    }

    /**
     * 取消节点全部 PENDING 任务（REJECT/RETURN/撤销级联）
     */
    public void cancelPendingTasks(EngineCtx ctx, String nodeId) {
        for (FlowTaskDO task : loadTasks(ctx, nodeId)) {
            if (TaskStatus.PENDING.equals(task.getStatus())) {
                finishTask(task, TaskStatus.CANCELED, null, null);
            }
        }
    }

    /**
     * 取消节点其余 PENDING 根任务（或签一票通过后）
     */
    public void cancelOtherPendingRootTasks(EngineCtx ctx, String nodeId, Long exceptTaskId) {
        for (FlowTaskDO task : loadTasks(ctx, nodeId)) {
            boolean pendingRoot = TaskStatus.PENDING.equals(task.getStatus()) && task.getParentTaskId() == null;
            if (pendingRoot && !task.getId().equals(exceptTaskId)) {
                finishTask(task, TaskStatus.CANCELED, null, null);
            }
        }
    }

    /**
     * 节点全部任务（含派生）
     */
    public List<FlowTaskDO> loadTasks(EngineCtx ctx, String nodeId) {
        return flowTaskMapper.selectList(new LambdaQueryWrapper<FlowTaskDO>()
                .eq(FlowTaskDO::getInstanceId, ctx.getInstance().getId())
                .eq(FlowTaskDO::getNodeId, nodeId));
    }

    /**
     * 节点集合上是否存在 PENDING 任务（并行汇合判定）
     */
    public boolean hasPendingTasks(EngineCtx ctx, Set<String> nodeIds) {
        if (nodeIds == null || nodeIds.isEmpty()) {
            return false;
        }
        Long count = flowTaskMapper.selectCount(new LambdaQueryWrapper<FlowTaskDO>()
                .eq(FlowTaskDO::getInstanceId, ctx.getInstance().getId())
                .in(FlowTaskDO::getNodeId, nodeIds)
                .eq(FlowTaskDO::getStatus, TaskStatus.PENDING));
        return count != null && count > 0;
    }

    /**
     * 实例是否存在 RUNNING 前置实例之外的状态更新并写终态（COMPLETED/REJECTED/CANCELED）
     */
    public void finishInstance(EngineCtx ctx, String status) {
        FlowInstanceDO update = new FlowInstanceDO();
        update.setId(ctx.getInstance().getId());
        update.setStatus(status);
        update.setFinishTime(LocalDateTime.now());
        flowInstanceMapper.updateById(update);
        ctx.getInstance().setStatus(status);
    }

    // ==================== 日志与抄送 ====================

    /**
     * 写审批记录（operator 为 null 时记 系统自动；node 可为 null）
     */
    public void writeLog(EngineCtx ctx, FlowTaskDO task, JsonNode node, String action,
                         UserDO operator, String comment, String detail) {
        FlowTaskLogDO logRecord = new FlowTaskLogDO();
        logRecord.setInstanceId(ctx.getInstance().getId());
        logRecord.setTaskId(task == null ? null : task.getId());
        logRecord.setNodeId(node == null ? (task == null ? null : task.getNodeId()) : idOf(node));
        logRecord.setNodeName(node == null ? (task == null ? null : task.getNodeName()) : nameOf(node));
        logRecord.setAction(action);
        logRecord.setOperatorId(operator == null ? null : operator.getId());
        logRecord.setOperatorName(operator == null ? SYSTEM_USER_NAME : FlowApproverResolver.displayName(operator));
        logRecord.setComment(comment);
        logRecord.setDetail(detail);
        flowTaskLogMapper.insert(logRecord);
    }

    /**
     * 落抄送记录（每人一条，UNREAD）
     */
    public void saveCc(EngineCtx ctx, Long taskId, List<UserDO> receivers) {
        if (receivers == null || receivers.isEmpty()) {
            return;
        }
        for (UserDO receiver : receivers) {
            FlowCcDO cc = new FlowCcDO();
            cc.setInstanceId(ctx.getInstance().getId());
            cc.setTaskId(taskId);
            cc.setReceiverId(receiver.getId());
            cc.setReceiverName(FlowApproverResolver.displayName(receiver));
            cc.setStatus("UNREAD");
            flowCcMapper.insert(cc);
        }
    }

    /**
     * 用户名称拼接（detail 文案用）
     */
    public static String joinNames(List<UserDO> users) {
        if (users == null || users.isEmpty()) {
            return "";
        }
        return users.stream()
                .map(FlowApproverResolver::displayName)
                .collect(Collectors.joining("、"));
    }
}
