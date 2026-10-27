package com.paddleocr.web.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 可退回目标节点 VO（对齐前端 getRejectTargets：当前节点之前路径上的 START/APPROVER）
 *
 * @author paddleocr
 * @date 2026-10-12
 */
@Data
@Schema(description = "可退回目标节点")
public class ReturnTargetVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "节点 ID（START=退回发起人重新提交）")
    private String nodeId;

    @Schema(description = "节点名称")
    private String nodeName;

    @Schema(description = "目标节点候选审批人（退回时须从中指定审批人；START 目标或空审批人节点为空列表）")
    private List<UserBriefVO> assigneeOptions;

    @Schema(description = "目标节点多方模式：OR=或签 AND=会签（START 目标为 null；前端据此渲染审批人多选）")
    private String multiMode;
}
