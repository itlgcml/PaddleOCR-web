package com.paddleocr.web.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * 审批动作请求 DTO（与前端 ApprovalActionPayload 一一对应）
 *
 * @author paddleocr
 * @date 2026-10-12
 */
@Data
@Schema(description = "审批动作请求")
public class TaskActionDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "动作：PASS/REJECT/COMMENT/COPY/TRANSFER/DELEGATE/ADD_SIGN/RETURN")
    @NotBlank(message = "动作类型不能为空")
    @Pattern(regexp = "^(PASS|REJECT|COMMENT|COPY|TRANSFER|DELEGATE|ADD_SIGN|RETURN)$",
            message = "动作类型仅支持 PASS/REJECT/COMMENT/COPY/TRANSFER/DELEGATE/ADD_SIGN/RETURN")
    private String action;

    @Schema(description = "审批意见；REJECT/RETURN 必填", maxLength = 512)
    @Size(max = 512, message = "审批意见不能超过 512 个字符")
    private String comment;

    @Schema(description = "目标人 ID：TRANSFER/DELEGATE/ADD_SIGN 必填")
    private String receiverId;

    @Schema(description = "抄送接收人 ID 集合：COPY 必填，≤20")
    @Size(max = 20, message = "抄送人不能超过 20 个")
    private List<String> receiverIds;

    @Schema(description = "退回目标节点 ID：RETURN 必填")
    private String targetNodeId;

    @Schema(description = "人工路由选中的分支列 ID：PASS 且下一节点为人工路由时必填")
    private String routeTargetId;

    @Schema(description = "人工并行勾选的分支列 ID 集合：PASS 且下一节点为人工并行（PARALLEL routing=MANUAL）时必填，至少一个")
    private List<String> routeTargetIds;

    @Schema(description = "人工路由/人工并行分支内首个审批节点的指定审批人（branchId → userId）：可选，" +
            "userId 须为对应分支 manualRouteOptions[].assigneeOptions 内的候选审批人；不传时按节点默认解析")
    private Map<String, String> routeAssignees;

    @Schema(description = "加签方向：ADD_SIGN 必填（BEFORE/AFTER）")
    @Pattern(regexp = "^(BEFORE|AFTER)$", message = "加签方向仅支持 BEFORE/AFTER")
    private String direction;
}
