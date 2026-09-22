package com.paddleocr.web.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import java.io.Serializable;

/**
 * 用户启用/禁用请求 DTO
 *
 * @author paddleocr
 * @date 2026-09-22
 */
@Data
@Schema(description = "用户启用/禁用请求")
public class UserStatusDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "状态：1=启用 0=禁用")
    @NotNull(message = "状态不能为空")
    @Min(value = 0, message = "状态仅允许 0 或 1")
    @Max(value = 1, message = "状态仅允许 0 或 1")
    private Integer status;
}
