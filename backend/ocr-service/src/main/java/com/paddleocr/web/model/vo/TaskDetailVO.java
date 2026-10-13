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

    @Schema(description = "当前用户=assignee 且任务 PENDING（前端据此启用操作栏）")
    private Boolean actionable;
}
