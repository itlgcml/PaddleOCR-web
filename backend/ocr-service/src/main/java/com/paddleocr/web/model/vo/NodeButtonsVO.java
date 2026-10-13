package com.paddleocr.web.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 节点审批动作按钮预览结果 VO
 *
 * @author paddleocr
 * @date 2026-10-12
 */
@Data
@Schema(description = "节点审批动作按钮预览结果")
public class NodeButtonsVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "节点 ID")
    private String nodeId;

    @Schema(description = "节点名称")
    private String nodeName;

    @Schema(description = "节点类型")
    private String nodeType;

    @Schema(description = "该节点配置的审批动作按钮（非 APPROVER 节点为空数组）")
    private List<ApproverButtonVO> buttons;
}
