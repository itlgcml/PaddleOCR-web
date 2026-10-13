package com.paddleocr.web.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 审批动作按钮 VO（节点配置快照透传，结构同前端 ApproverButton）
 *
 * @author paddleocr
 * @date 2026-10-12
 */
@Data
@Schema(description = "审批动作按钮")
public class ApproverButtonVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "按钮 key：PASS/REJECT/TRANSFER/DELEGATE/ADD_SIGN_BEFORE/ADD_SIGN_AFTER/RETURN")
    private String key;

    @Schema(description = "按钮文案")
    private String label;

    @Schema(description = "是否启用")
    private Boolean enabled;
}
