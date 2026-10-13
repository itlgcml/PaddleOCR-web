package com.paddleocr.web.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import java.io.Serializable;
import java.util.Map;

/**
 * 下一节点审批人预览查询 DTO
 *
 * @author paddleocr
 * @date 2026-10-12
 */
@Data
@Schema(description = "下一节点审批人预览查询")
public class NextApproverQueryDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "流程定义 ID（数字串）")
    @NotBlank(message = "流程 ID 不能为空")
    @Pattern(regexp = "^\\d{1,19}$", message = "流程 ID 必须为数字串")
    private String processId;

    @Schema(description = "当前节点 ID；缺省时默认返回发起后第一个审批节点的审批人", maxLength = 64)
    @Size(max = 64, message = "节点 ID 不能超过 64 位")
    private String nodeId;

    @Schema(description = "条件值 Map：键为条件规则的英文 fieldKey，值为条件值；用于 CONDITIONS 真实求值")
    @Size(max = 50, message = "条件值键值对不能超过 50 个")
    private Map<String, String> conditions;
}
