package com.paddleocr.web.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import java.io.Serializable;
import java.util.Map;

/**
 * 节点审批动作按钮预览查询 DTO
 *
 * @author paddleocr
 * @date 2026-10-12
 */
@Data
@Schema(description = "节点审批动作按钮预览查询")
public class NodeButtonsQueryDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "流程定义 ID（数字串）")
    @NotBlank(message = "流程 ID 不能为空")
    @Pattern(regexp = "^\\d{1,19}$", message = "流程 ID 必须为数字串")
    private String processId;

    @Schema(description = "节点 ID；缺省时默认取发起后第一个审批节点", maxLength = 64)
    @Size(max = 64, message = "节点 ID 不能超过 64 位")
    private String nodeId;

    @Schema(description = "条件值 Map（同下一审批人预览）：仅影响 nodeId 缺省时首个审批节点的定位")
    @Size(max = 50, message = "条件值键值对不能超过 50 个")
    private Map<String, String> conditions;
}
