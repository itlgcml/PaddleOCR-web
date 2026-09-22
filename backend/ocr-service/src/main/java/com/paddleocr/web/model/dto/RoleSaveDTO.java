package com.paddleocr.web.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import java.io.Serializable;

/**
 * 角色新增请求 DTO
 *
 * @author paddleocr
 * @date 2026-09-22
 */
@Data
@Schema(description = "角色新增请求")
public class RoleSaveDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "角色编码（建后不可改，不得为 ADMIN/USER）", maxLength = 64)
    @NotBlank(message = "角色编码不能为空")
    @Size(min = 4, max = 64, message = "角色编码长度须为 4~64 位")
    @Pattern(regexp = "^[A-Z][A-Z0-9_]*$", message = "角色编码须为大写字母开头的大写字母/数字/下划线")
    private String roleCode;

    @Schema(description = "角色名称", maxLength = 64)
    @NotBlank(message = "角色名称不能为空")
    @Size(max = 64, message = "角色名称不能超过 64 个字符")
    private String roleName;

    @Schema(description = "角色描述", maxLength = 255)
    @Size(max = 255, message = "角色描述不能超过 255 个字符")
    private String description;
}
