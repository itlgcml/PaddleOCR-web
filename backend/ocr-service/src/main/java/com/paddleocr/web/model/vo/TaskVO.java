package com.paddleocr.web.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 审批任务列表行 VO（待办/已办共用）
 *
 * @author paddleocr
 * @date 2026-10-12
 */
@Data
@Schema(description = "审批任务列表行")
public class TaskVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "任务 ID")
    private String id;

    @Schema(description = "实例 ID")
    private String instanceId;

    @Schema(description = "流程名称")
    private String processName;

    @Schema(description = "业务单据类型")
    private String bizType;

    @Schema(description = "业务单据 ID")
    private String bizId;

    @Schema(description = "业务标题")
    private String bizTitle;

    @Schema(description = "当前节点名")
    private String nodeName;

    @Schema(description = "多实例模式：OR=或签 AND=会签")
    private String multiMode;

    @Schema(description = "任务派生类型：NORMAL=普通 BEFORE_SIGN/AFTER_SIGN=前/后加签 TRANSFER=转办 DELEGATE=委托")
    private String signType;

    @Schema(description = "发起人名称")
    private String startUserName;

    @Schema(description = "任务到达时间")
    private LocalDateTime createTime;

    @Schema(description = "任务状态：PENDING/PASSED/REJECTED/CANCELED/TRANSFERRED")
    private String status;

    @Schema(description = "完成动作：PASS/REJECT/RETURN/TRANSFER/DELEGATE/SUBMIT")
    private String action;

    @Schema(description = "完成时间")
    private LocalDateTime finishTime;

    @Schema(description = "审批意见（任务完成时冗余）")
    private String comment;
}
