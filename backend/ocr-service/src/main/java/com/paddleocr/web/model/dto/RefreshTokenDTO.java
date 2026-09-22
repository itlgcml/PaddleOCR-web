package com.paddleocr.web.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import java.io.Serializable;

/**
 * 刷新令牌请求 DTO
 *
 * @author paddleocr
 * @date 2026-09-22
 */
@Data
@Schema(description = "刷新令牌请求")
public class RefreshTokenDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "登录时下发的刷新令牌")
    @NotBlank(message = "刷新令牌不能为空")
    private String refreshToken;
}
