package com.paddleocr.web.service.approval;

/**
 * 审批动作常量（值与 flow_task_log.action / flow_task.action 列及前端 ApprovalActionKey 契约一致）
 *
 * @author paddleocr
 * @date 2026-10-12
 */
public final class TaskAction {

    /** 发起（系统/发起人动作，仅记日志） */
    public static final String START = "START";

    /** 通过 */
    public static final String PASS = "PASS";

    /** 拒绝（按节点配置退回或终止） */
    public static final String REJECT = "REJECT";

    /** 主动退回（需选目标节点） */
    public static final String RETURN = "RETURN";

    /** 转办（任务转移给他人） */
    public static final String TRANSFER = "TRANSFER";

    /** 委托（V1 按转办语义处理，仅日志区分） */
    public static final String DELEGATE = "DELEGATE";

    /** 加签（direction 区分 BEFORE/AFTER） */
    public static final String ADD_SIGN = "ADD_SIGN";

    /** 重新提交（退回发起人后发起人再次提交） */
    public static final String SUBMIT = "SUBMIT";

    /** 撤销（发起人） */
    public static final String CANCEL = "CANCEL";

    /** 评论（仅记日志，不影响流转） */
    public static final String COMMENT = "COMMENT";

    /** 抄送（落 flow_cc，不影响流转） */
    public static final String COPY = "COPY";

    /** 空审批人自动通过/条件容器直通/TRANSACT 直通（系统动作，仅落 flow_task.action，不单独落审批记录） */
    public static final String AUTO_PASS = "AUTO_PASS";

    /** 引擎降级/异常路径说明（系统动作，聚合进本次操作记录 detail，不单独落 flow_task_log） */
    public static final String ENGINE = "ENGINE";

    /** 加签方向-前加签 */
    public static final String SIGN_BEFORE = "BEFORE";

    /** 加签方向-后加签 */
    public static final String SIGN_AFTER = "AFTER";

    private TaskAction() {
    }
}
