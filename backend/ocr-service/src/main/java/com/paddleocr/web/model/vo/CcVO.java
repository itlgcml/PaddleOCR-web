package com.paddleocr.web.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 抄送记录列表行 VO（待阅/已阅共用）
 *
 * @author paddleocr
 * @date 2026-10-12
 */
@Data
@Schema(description = "抄送记录列表行")
public class CcVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "抄送记录 ID")
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

    @Schema(description = "触发抄送人/发起人名称")
    private String fromUserName;

    @Schema(description = "抄送备注")
    private String comment;

    @Schema(description = "抄送时间")
    private LocalDateTime createTime;

    @Schema(description = "阅读时间")
    private LocalDateTime readTime;
}
