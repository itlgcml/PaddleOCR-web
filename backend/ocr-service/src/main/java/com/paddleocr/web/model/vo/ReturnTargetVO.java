package com.paddleocr.web.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

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
}
