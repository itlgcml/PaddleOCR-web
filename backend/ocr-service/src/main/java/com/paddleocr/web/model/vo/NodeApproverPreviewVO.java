package com.paddleocr.web.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 预览节点审批人 VO（下一审批节点组，并行时多元素）
 *
 * @author paddleocr
 * @date 2026-10-12
 */
@Data
@Schema(description = "预览节点审批人")
public class NodeApproverPreviewVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "节点 ID")
    private String nodeId;

    @Schema(description = "节点名称")
    private String nodeName;

    @Schema(description = "多实例模式：OR=或签 AND=会签")
    private String multiMode;

    @Schema(description = "审批人清单（解析后）")
    private List<UserBriefVO> approvers;
}
