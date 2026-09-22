package com.paddleocr.web.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import java.io.Serializable;

/**
 * 机构新增请求 DTO
 *
 * @author paddleocr
 * @date 2026-09-22
 */
@Data
@Schema(description = "机构新增请求")
public class OrgSaveDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "父机构 ID（须存在且启用，不允许以自身/后代为父）")
    @NotNull(message = "父机构 ID 不能为空")
    private Long parentId;

    @Schema(description = "机构编码（建后不可改）", maxLength = 32)
    @NotBlank(message = "机构编码不能为空")
    @Size(max = 32, message = "机构编码不能超过 32 个字符")
    @Pattern(regexp = "^[A-Z][A-Z0-9_]*$", message = "机构编码须为大写字母开头的大写字母/数字/下划线")
    private String orgCode;

    @Schema(description = "机构名称", maxLength = 64)
    @NotBlank(message = "机构名称不能为空")
    @Size(max = 64, message = "机构名称不能超过 64 个字符")
    private String orgName;

    @Schema(description = "同级排序（越小越靠前，默认 0）")
    @Min(value = 0, message = "排序值不能为负数")
    private Integer sort;
}
