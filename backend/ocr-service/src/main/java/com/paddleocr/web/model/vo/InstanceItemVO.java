package com.paddleocr.web.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 我发起的实例列表行 VO
 *
 * @author paddleocr
 * @date 2026-10-12
 */
@Data
@Schema(description = "我发起的实例列表行")
public class InstanceItemVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "实例 ID")
    private String id;

    @Schema(description = "流程名称（冗余快照）")
    private String processName;

    @Schema(description = "业务单据类型")
    private String bizType;

    @Schema(description = "业务单据 ID")
    private String bizId;

    @Schema(description = "业务标题（冗余快照）")
    private String bizTitle;

    @Schema(description = "实例状态：RUNNING/COMPLETED/REJECTED/CANCELED")
    private String status;

    @Schema(description = "发起时间")
    private LocalDateTime startTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;

    @Schema(description = "结束时间")
    private LocalDateTime finishTime;

    @Schema(description = "当前待办人姓名（RUNNING 时按 PENDING 根任务汇总去重）")
    private List<String> currentApproverNames;
}
