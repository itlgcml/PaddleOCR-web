package com.paddleocr.web.service.approval;

/**
 * 任务状态常量（值与 flow_task.status 列及前端契约一致，避免魔法值散落）
 *
 * @author paddleocr
 * @date 2026-10-12
 */
public final class TaskStatus {

    /** 待办 */
    public static final String PENDING = "PENDING";

    /** 通过（含重新提交 SUBMIT 的发起人任务） */
    public static final String PASSED = "PASSED";

    /** 已拒绝 */
    public static final String REJECTED = "REJECTED";

    /** 已取消（或签被抢先/驳回级联/撤销级联） */
    public static final String CANCELED = "CANCELED";

    /** 已转出（转办/委托后由替身任务顶替） */
    public static final String TRANSFERRED = "TRANSFERRED";

    private TaskStatus() {
    }
}
