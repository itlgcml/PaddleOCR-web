package com.paddleocr.web.service.approval.engine;

import com.paddleocr.web.mapper.entity.ProcessDO;
import com.paddleocr.web.mapper.entity.ProcessVersionDO;
import com.paddleocr.web.mapper.entity.UserDO;
import com.paddleocr.web.model.dto.TaskActionDTO;
import com.paddleocr.web.model.vo.NextApproverVO;

import java.util.List;
import java.util.Map;

/**
 * 审批流引擎（运行态推进内核）
 *
 * <p>遍历规则基于前端 WorkflowDoc 快照（root + childNode 主干链 + conditionNodes 分支列），
 * 引擎只认 JSON 快照不映射 POJO，配置域结构演进无需同步双份模型。</p>
 *
 * @author paddleocr
 * @date 2026-10-12
 */
public interface FlowEngine {

    /**
     * 发起：建实例（冻结版本快照与表单快照）+ START 日志 + 发起抄送 + 从 root 后继推进。
     * 流程发布/发起范围/单据存在性/RUNNING 唯一性校验由 FlowInstanceService 前置完成。
     *
     * @param process      流程定义（PUBLISHED）
     * @param version      当前发布版本（不可变快照）
     * @param bizType      业务单据类型
     * @param bizId        业务单据 ID
     * @param startUser    发起人（启用）
     * @param bizTitle     业务标题（冗余快照）
     * @param formJson     表单数据 JSON 快照（条件求值与详情展示）
     * @param ccUsers      发起抄送人（已校验），可为空
     * @param startComment 发起备注（START 日志）
     * @return 实例 ID
     */
    Long startInstance(ProcessDO process, ProcessVersionDO version, String bizType, Long bizId,
                       UserDO startUser, String bizTitle, String formJson,
                       List<UserDO> ccUsers, String startComment);

    /**
     * 统一动作处理（实例级推进锁内执行）：PASS/REJECT/RETURN/TRANSFER/DELEGATE/ADD_SIGN/COMMENT/COPY。
     * 任务存在性/assignee/PENDING/按钮 enabled 校验由引擎完成；操作人为当前登录用户（已查库）。
     *
     * @param taskId   任务 ID
     * @param dto      动作载荷（与前端 ApprovalActionPayload 对齐）
     * @param operator 操作人
     */
    void handleAction(Long taskId, TaskActionDTO dto, UserDO operator);

    /**
     * 撤销：级联取消全部 PENDING 任务 + 实例终态 CANCELED + 日志。
     * 发起人身份与 RUNNING 状态校验由 FlowInstanceService 前置完成。
     *
     * @param instanceId 实例 ID
     * @param operator   操作人（发起人）
     * @param comment    撤销原因
     */
    void cancelInstance(Long instanceId, UserDO operator, String comment);

    /**
     * 下一审批节点预览（无副作用 dryRun）：nodeId 为 null 时从 root 后继推进。
     * conditions（fieldKey→条件值 Map）非空时 CONDITIONS 真实求值（缺失键规则不满足），
     * 缺省按默认列模拟；AUTO_PASS 空审批人节点跳过继续；PARALLEL 汇聚各分支首组审批节点；
     * 推进到链尾无审批节点返回 ended=true。
     *
     * @param processId     流程定义 ID
     * @param nodeId        当前节点 ID（可 null）
     * @param currentUserId 当前登录用户（INITIATOR 来源代入）
     * @param conditions    条件值 Map（可 null）
     * @return 预览结果
     */
    NextApproverVO previewNextApprovers(Long processId, String nodeId, Long currentUserId,
                                        Map<String, String> conditions);
}
