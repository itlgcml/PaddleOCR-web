package com.paddleocr.web.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 下一节点审批人预览结果 VO
 *
 * @author paddleocr
 * @date 2026-10-12
 */
@Data
@Schema(description = "下一节点审批人预览结果")
public class NextApproverVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "true=后续无审批节点（nodes 为空数组，前端显示\"通过后流程结束\"）")
    private Boolean ended;

    @Schema(description = "下一审批节点组（线性流程恒 1 个；并行激活时多个）")
    private List<NodeApproverPreviewVO> nodes;
}
