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
 * 发起审批请求 DTO
 *
 * @author paddleocr
 * @date 2026-10-12
 */
@Data
@Schema(description = "发起审批请求")
public class InstanceStartDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "流程编码", maxLength = 64)
    @NotBlank(message = "流程编码不能为空")
    @Size(max = 64, message = "流程编码不能超过 64 位")
    private String processCode;

    @Schema(description = "业务单据类型：V1 仅 LAW_FIRM_SELECT")
    @NotBlank(message = "单据类型不能为空")
    @Pattern(regexp = "^LAW_FIRM_SELECT$", message = "单据类型仅支持 LAW_FIRM_SELECT")
    private String bizType;

    @Schema(description = "业务单据 ID（数字串）")
    @NotBlank(message = "单据 ID 不能为空")
    @Pattern(regexp = "^\\d{1,19}$", message = "单据 ID 必须为数字串")
    private String bizId;

    @Schema(description = "发起抄送人 ID 集合，≤20")
    @Size(max = 20, message = "抄送人不能超过 20 个")
    private List<String> ccUserIds;

    @Schema(description = "发起备注（START 日志）", maxLength = 512)
    @Size(max = 512, message = "备注不能超过 512 个字符")
    private String comment;

    @Schema(description = "发起时为第一审批阶段节点指定的审批人（nodeId → userIds，可选）：目标为 next-approvers " +
            "预览返回的节点（普通首审批节点 / 并行各列首组 / IMMEDIATE 子流程主/子双线 / WAIT 穿透子 doc 首组）；" +
            "或签节点传单个、会签节点传多个；userId 须为对应节点候选审批人，不传时按节点默认解析")
    private Map<String, List<String>> startAssignees;
}
