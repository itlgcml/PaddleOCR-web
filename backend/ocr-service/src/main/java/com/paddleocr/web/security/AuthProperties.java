package com.paddleocr.web.security;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * JWT 认证配置项（前缀 security.jwt）
 *
 * @author paddleocr
 * @date 2026-09-22
 */
@Data
@Component
@ConfigurationProperties(prefix = "security.jwt")
public class AuthProperties {

    /** HS256 签名密钥（禁止明文，环境变量注入，长度不小于 32 字符） */
    private String secret;

    /** accessToken 有效秒数（默认 2 小时） */
    private Integer accessTokenTtl = 7200;

    /** refreshToken 有效秒数（默认 7 天） */
    private Integer refreshTokenTtl = 604800;

    /** 连续登录失败次数上限（达到后锁定账号） */
    private Integer maxFailedAttempts = 5;

    /** 账号锁定时长（分钟） */
    private Integer lockDurationMinutes = 15;

    /** 鉴权开关（应急降级：false 时 /api/** 全量放行并跳过黑名单校验，生产慎用） */
    private Boolean enabled = true;
}
