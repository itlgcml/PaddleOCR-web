package com.paddleocr.web.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 审批记录时间线行 VO（含 START/AUTO_PASS/ENGINE 系统动作）
 *
 * @author paddleocr
 * @date 2026-10-12
 */
@Data
@Schema(description = "审批记录时间线行")
public class TaskRecordVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "记录 ID")
    private String id;

    @Schema(description = "动作：START/PASS/REJECT/RETURN/TRANSFER/DELEGATE/ADD_SIGN/SUBMIT/CANCEL/COMMENT/COPY/AUTO_PASS/ENGINE")
    private String action;

    @Schema(description = "节点名称")
    private String nodeName;

    @Schema(description = "操作人名称（系统动作为 系统自动）")
    private String operatorName;

    @Schema(description = "意见")
    private String comment;

    @Schema(description = "动作摘要：如 退回到【部门审批】")
    private String detail;

    @Schema(description = "记录时间")
    private LocalDateTime createTime;
}
