package com.paddleocr.web.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.Digits;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 律所选聘新增并提交审批请求 DTO：单事务内完成建单与发起，任一步失败整体回滚
 *
 * <p>单据类型由后端固定为 LAW_FIRM_SELECT，不接收前端传值。</p>
 *
 * @author paddleocr
 * @date 2026-10-19
 */
@Data
@Schema(description = "律所选聘新增并提交审批请求")
public class LawFirmSelectSubmitDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "标题", maxLength = 128)
    @NotBlank(message = "标题不能为空")
    @Size(min = 1, max = 128, message = "标题长度须为 1~128 位")
    private String title;

    @Schema(description = "选聘金额（元），最多两位小数")
    @NotNull(message = "选聘金额不能为空")
    @DecimalMin(value = "0.01", message = "选聘金额须大于 0")
    @Digits(integer = 10, fraction = 2, message = "选聘金额整数部分不超过 10 位、小数不超过 2 位")
    private BigDecimal amount;

    @Schema(description = "选聘原因", maxLength = 512)
    @NotBlank(message = "选聘原因不能为空")
    @Size(min = 1, max = 512, message = "选聘原因长度须为 1~512 位")
    private String reason;

    @Schema(description = "流程编码", maxLength = 64)
    @NotBlank(message = "流程编码不能为空")
    @Size(max = 64, message = "流程编码不能超过 64 位")
    private String processCode;

    @Schema(description = "发起抄送人 ID 集合，≤20")
    @Size(max = 20, message = "抄送人不能超过 20 个")
    private List<String> ccUserIds;

    @Schema(description = "发起备注（START 日志）", maxLength = 512)
    @Size(max = 512, message = "备注不能超过 512 个字符")
    private String comment;

    @Schema(description = "发起时为第一审批阶段节点指定的审批人（nodeId → userIds，可选），口径同 InstanceStartDTO.startAssignees")
    private Map<String, List<String>> startAssignees;
}
