package com.paddleocr.web.service.approval;

/**
 * 流程节点进度状态常量（NodeProgressVO.status 值域，与前端契约一致，避免魔法值散落）
 *
 * @author paddleocr
 * @date 2026-10-28
 */
public final class NodeProgressStatus {

    /** 已走过（节点任务已办理通过，含系统自动通过） */
    public static final String PASSED = "PASSED";

    /** 进行中（实例运行中且该节点存在待办任务，或子流程实例运行中） */
    public static final String CURRENT = "CURRENT";

    /** 已拒绝（节点任务被拒绝，实例终止） */
    public static final String REJECTED = "REJECTED";

    /** 已取消（仅子流程节点：对应子实例被撤销/退回级联取消） */
    public static final String CANCELED = "CANCELED";

    /** 未开始（流程尚未到达该节点） */
    public static final String PENDING = "PENDING";

    private NodeProgressStatus() {
    }
}
