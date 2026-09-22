package com.paddleocr.web.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import java.io.Serializable;

/**
 * 登录请求 DTO
 *
 * @author paddleocr
 * @date 2026-09-22
 */
@Data
@Schema(description = "登录请求")
public class LoginDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "用户名", maxLength = 32)
    @NotBlank(message = "用户名不能为空")
    @Size(min = 4, max = 32, message = "用户名长度须为 4~32 位")
    private String username;

    @Schema(description = "密码", maxLength = 20)
    @NotBlank(message = "密码不能为空")
    @Size(min = 8, max = 20, message = "密码长度须为 8~20 位")
    private String password;
}
