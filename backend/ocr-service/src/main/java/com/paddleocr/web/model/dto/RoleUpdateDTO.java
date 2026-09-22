package com.paddleocr.web.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import java.io.Serializable;

/**
 * 角色修改请求 DTO（roleCode 建后不可改，DTO 不提供字段；内置角色禁止任何修改）
 *
 * @author paddleocr
 * @date 2026-09-22
 */
@Data
@Schema(description = "角色修改请求")
public class RoleUpdateDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "角色名称", maxLength = 64)
    @NotBlank(message = "角色名称不能为空")
    @Size(max = 64, message = "角色名称不能超过 64 个字符")
    private String roleName;

    @Schema(description = "角色描述", maxLength = 255)
    @Size(max = 255, message = "角色描述不能超过 255 个字符")
    private String description;

    @Schema(description = "状态：1=启用 0=停用（停用后不可再分配给用户）")
    @Min(value = 0, message = "状态仅允许 0 或 1")
    @Max(value = 1, message = "状态仅允许 0 或 1")
    private Integer status;
}
