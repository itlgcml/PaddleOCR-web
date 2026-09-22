package com.paddleocr.web.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 登录出参 VO
 *
 * @author paddleocr
 * @date 2026-09-22
 */
@Data
@Schema(description = "登录结果")
public class LoginVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "访问令牌（默认 2 小时）")
    private String accessToken;

    @Schema(description = "刷新令牌（默认 7 天）")
    private String refreshToken;

    @Schema(description = "令牌类型，固定 Bearer")
    private String tokenType;

    @Schema(description = "accessToken 有效秒数")
    private Integer expiresIn;

    @Schema(description = "当前用户信息")
    private UserInfoVO user;
}
