package com.paddleocr.web.service.approval;

/**
 * 实例状态常量（值与 flow_instance.status 列及前端契约一致，避免魔法值散落）
 *
 * @author paddleocr
 * @date 2026-10-12
 */
public final class InstanceStatus {

    /** 进行中（可审批/可撤销） */
    public static final String RUNNING = "RUNNING";

    /** 已完成（全部审批节点通过） */
    public static final String COMPLETED = "COMPLETED";

    /** 已拒绝（REJECT 终止语义，无退回目标时结束） */
    public static final String REJECTED = "REJECTED";

    /** 已撤销（发起人撤销） */
    public static final String CANCELED = "CANCELED";

    private InstanceStatus() {
    }
}
