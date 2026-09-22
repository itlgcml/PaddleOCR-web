package com.paddleocr.web.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import java.io.Serializable;

/**
 * 注册请求 DTO
 *
 * @author paddleocr
 * @date 2026-09-22
 */
@Data
@Schema(description = "注册请求")
public class RegisterDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "用户名（登录用，全库唯一）", maxLength = 32)
    @NotBlank(message = "用户名不能为空")
    @Size(min = 4, max = 32, message = "用户名长度须为 4~32 位")
    @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "用户名仅支持字母、数字、下划线")
    private String username;

    @Schema(description = "密码（明文仅存在于传输过程，落库前 BCrypt 加密）", maxLength = 20)
    @NotBlank(message = "密码不能为空")
    @Size(min = 8, max = 20, message = "密码长度须为 8~20 位")
    private String password;

    @Schema(description = "昵称（缺省时取用户名）", maxLength = 64)
    @Size(max = 64, message = "昵称不能超过 64 个字符")
    private String nickname;

    @Schema(description = "邮箱", maxLength = 128)
    @Email(message = "邮箱格式不正确")
    @Size(max = 128, message = "邮箱不能超过 128 个字符")
    private String email;

    @Schema(description = "所属机构 ID（缺省挂 DEFAULT 默认机构）")
    private Long orgId;
}
