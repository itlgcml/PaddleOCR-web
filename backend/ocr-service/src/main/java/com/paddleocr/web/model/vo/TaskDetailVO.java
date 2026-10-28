package com.paddleocr.web.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.util.List;

/**
 * 任务办理视图 VO（= 实例详情全量 + 任务本体/按钮/退回目标/可操作性）
 *
 * @author paddleocr
 * @date 2026-10-12
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "任务办理视图")
public class TaskDetailVO extends InstanceDetailVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "任务本体（含节点信息与意见）")
    private TaskVO task;

    @Schema(description = "节点按钮配置快照透传（enabled 决定操作栏渲染）")
    private List<ApproverButtonVO> buttons;

    @Schema(description = "可退回目标（仅 RETURN 按钮启用时非空）")
    private List<ReturnTargetVO> returnTargets;

    @Schema(description = "人工路由选项（当前节点通过后需人工选择下一步时非空，PASS 弹窗据此渲染下一步下拉）")
    private List<ManualRouteOptionVO> manualRouteOptions;

    @Schema(description = "人工路由是否多选：下一节点为人工并行（PARALLEL routing=MANUAL）时 true，PASS 携带 routeTargetIds 多选；人工条件路由为单选 routeTargetId")
    private Boolean manualRouteMultiple;

    @Schema(description = "下一节点为子流程（启动后继续）时可指定审批人目标（子流程首节点 + 父链下一节点，PASS 弹窗分开展示并可分别指定；其他场景为空）")
    private List<SubProcessApproverOptionVO> subProcessApproverOptions;

    @Schema(description = "下一节点（普通审批节点，穿透直通办理节点）可指定审批人目标（单元素，PASS 弹窗渲染「下一节点审批人」选择；与人工路由/子流程选项按链后继类型互斥）")
    private List<NextApproverOptionVO> nextApproverOptions;

    @Schema(description = "当前任务直接派生且 PENDING 的加签任务（发起加签人视角：同意前提示加签人未审，可携带 targetTaskId 发起 REDUCE_SIGN 减签）")
    private List<PendingSignTaskVO> pendingSignTasks;

    @Schema(description = "当前用户=assignee 且任务 PENDING（前端据此启用操作栏）")
    private Boolean actionable;
}
