package com.paddleocr.web.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 审批实例详情 VO
 *
 * @author paddleocr
 * @date 2026-10-12
 */
@Data
@Schema(description = "审批实例详情")
public class InstanceDetailVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "实例 ID")
    private String id;

    @Schema(description = "流程定义 ID")
    private String processId;

    @Schema(description = "流程编码")
    private String processCode;

    @Schema(description = "流程名称")
    private String processName;

    @Schema(description = "发起时的流程版本号，如 v2")
    private String processVersion;

    @Schema(description = "业务单据类型")
    private String bizType;

    @Schema(description = "业务单据 ID")
    private String bizId;

    @Schema(description = "业务标题（发起时冗余快照）")
    private String bizTitle;

    @Schema(description = "实例状态：RUNNING/COMPLETED/REJECTED/CANCELED")
    private String status;

    @Schema(description = "发起人 ID")
    private String startUserId;

    @Schema(description = "发起人名称")
    private String startUserName;

    @Schema(description = "发起时间")
    private LocalDateTime startTime;

    @Schema(description = "结束时间")
    private LocalDateTime finishTime;

    @Schema(description = "表单快照 JSON（详情页业务表单渲染）")
    private String formSnapshotJson;

    @Schema(description = "父实例 ID（当前实例为子流程实例时非空，前端据此渲染返回父实例入口）")
    private String parentInstanceId;

    @Schema(description = "子流程节点名（当前实例为子流程实例时非空，前端渲染「子流程」徽标）")
    private String subProcessName;

    @Schema(description = "版本快照 WorkflowDoc JSON（前端复用 workflow 画布渲染流程图）")
    private String designJson;

    @Schema(description = "本实例直接派生的子流程实例摘要（按创建时间升序）")
    private List<SubInstanceSummaryVO> subInstances;

    @Schema(description = "审批记录时间线")
    private List<TaskRecordVO> records;

    @Schema(description = "节点进度列表（流程图页签：按设计快照办理类节点标记走过/进行中/未开始）")
    private List<NodeProgressVO> nodeProgress;

    @Schema(description = "当前用户在该实例的待办任务 ID（无则 null）")
    private String myPendingTaskId;

    @Schema(description = "当前用户是否可撤销（=发起人 且 RUNNING）")
    private Boolean canCancel;
}
