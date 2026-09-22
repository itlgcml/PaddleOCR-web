package com.paddleocr.web.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 登出请求 DTO（refreshToken 可选，传入则一并吊销）
 *
 * @author paddleocr
 * @date 2026-09-22
 */
@Data
@Schema(description = "登出请求")
public class LogoutDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "刷新令牌（传入则一并吊销）")
    private String refreshToken;
}
