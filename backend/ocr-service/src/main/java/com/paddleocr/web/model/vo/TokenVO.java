package com.paddleocr.web.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 刷新令牌出参 VO
 *
 * @author paddleocr
 * @date 2026-09-22
 */
@Data
@Schema(description = "刷新令牌结果")
public class TokenVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "新访问令牌")
    private String accessToken;

    @Schema(description = "令牌类型，固定 Bearer")
    private String tokenType;

    @Schema(description = "accessToken 有效秒数")
    private Integer expiresIn;
}
