package com.paddleocr.web.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 业务单据审批状态 VO（律所选聘列表徽标）
 *
 * @author paddleocr
 * @date 2026-10-12
 */
@Data
@Schema(description = "业务单据审批状态")
public class BizApprovalStatusVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "业务单据 ID")
    private String bizId;

    @Schema(description = "进行中实例 ID")
    private String instanceId;

    @Schema(description = "实例状态：RUNNING/COMPLETED/REJECTED/CANCELED（取最新一条实例）")
    private String status;

    @Schema(description = "当前待办人姓名（RUNNING 时）")
    private List<String> currentApproverNames;
}
