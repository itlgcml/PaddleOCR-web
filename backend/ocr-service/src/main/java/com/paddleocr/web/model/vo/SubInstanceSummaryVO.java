package com.paddleocr.web.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 子流程实例摘要（父实例详情的子流程卡片）
 *
 * @author paddleocr
 * @date 2026-10-22
 */
@Data
@Schema(description = "子流程实例摘要")
public class SubInstanceSummaryVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "子实例 ID")
    private String id;

    @Schema(description = "子实例流程名（父流程名·子流程节点名）")
    private String name;

    @Schema(description = "子流程节点名（SUB_PROCESS 节点 name）")
    private String subProcessName;

    @Schema(description = "推进模式：WAIT=等待子流程完成后父流程继续 IMMEDIATE=启动即继续（旁路）")
    private String advanceMode;

    @Schema(description = "子实例状态：RUNNING/COMPLETED/REJECTED/CANCELED")
    private String status;

    @Schema(description = "子实例创建时间")
    private LocalDateTime createTime;

    @Schema(description = "子实例结束时间（终态时非空）")
    private LocalDateTime finishTime;
}
