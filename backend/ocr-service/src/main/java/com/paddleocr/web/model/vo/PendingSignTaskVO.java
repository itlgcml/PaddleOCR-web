package com.paddleocr.web.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 待办理加签任务 VO（发起加签人视角：同意前提示加签人未审，可删除减签）
 *
 * @author paddleocr
 * @date 2026-10-19
 */
@Data
@Schema(description = "待办理加签任务")
public class PendingSignTaskVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "加签任务 ID（REDUCE_SIGN 的 targetTaskId 回传）")
    private String taskId;

    @Schema(description = "派生类型：BEFORE_SIGN=向前加签 AFTER_SIGN=向后加签")
    private String signType;

    @Schema(description = "加签人 ID")
    private String assigneeId;

    @Schema(description = "加签人姓名")
    private String assigneeName;

    @Schema(description = "任务到达时间")
    private LocalDateTime createTime;
}
