package com.paddleocr.web.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import java.io.Serializable;

/**
 * 机构修改请求 DTO（orgCode、parentId 本期禁止修改，DTO 不提供字段）
 *
 * @author paddleocr
 * @date 2026-09-22
 */
@Data
@Schema(description = "机构修改请求")
public class OrgUpdateDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "机构名称", maxLength = 64)
    @NotBlank(message = "机构名称不能为空")
    @Size(max = 64, message = "机构名称不能超过 64 个字符")
    private String orgName;

    @Schema(description = "同级排序（越小越靠前）")
    @Min(value = 0, message = "排序值不能为负数")
    private Integer sort;

    @Schema(description = "状态：1=启用 0=停用（停用后不可作为新增/调入的目标机构）")
    @Min(value = 0, message = "状态仅允许 0 或 1")
    @Max(value = 1, message = "状态仅允许 0 或 1")
    private Integer status;
}
