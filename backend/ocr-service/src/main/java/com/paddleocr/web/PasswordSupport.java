package com.paddleocr.web;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * 密码加密工具：仅依赖 spring-security-crypto 子包（跨模块红线，ocr-service 禁依赖 security starter）
 *
 * <p>注册加密与登录比对同一算法同一强度（BCrypt strength=10，自带随机盐）</p>
 *
 * @author paddleocr
 * @date 2026-09-22
 */
public final class PasswordSupport {

    /** BCrypt 加密强度（与 ocr-web 的 BCryptPasswordEncoder Bean 保持一致） */
    private static final int BCRYPT_STRENGTH = 10;

    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder(BCRYPT_STRENGTH);

    private PasswordSupport() {
    }

    /**
     * 明文密码加密为 BCrypt 密文（定长 60 字符）
     *
     * @param rawPassword 明文密码
     * @return BCrypt 密文
     */
    public static String encode(String rawPassword) {
        return ENCODER.encode(rawPassword);
    }
}
