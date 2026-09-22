package com.paddleocr.web.security;

import com.paddleocr.web.common.ResultCode;
import org.springframework.security.core.AuthenticationException;

/**
 * 认证失败异常：携带 ResultCode（40101/40102/40103/40023）
 *
 * <p>JwtAuthenticationFilter 内 Token 异常手动接管输出的载体——该 Filter 挂在
 * UsernamePasswordAuthenticationFilter 之前、ExceptionTranslationFilter 之后，
 * 抛出的 AuthenticationException 传播不到 RestAuthEntryPoint，必须 Filter 内
 * try-catch 后手动调用 restAuthEntryPoint.commence() 写出</p>
 *
 * @author paddleocr
 * @date 2026-09-22
 */
public class AuthFailureException extends AuthenticationException {

    private static final long serialVersionUID = 1L;

    /** 携带的错误码（RestAuthEntryPoint 优先读取） */
    private final ResultCode resultCode;

    public AuthFailureException(ResultCode resultCode) {
        super(resultCode.getMessage());
        this.resultCode = resultCode;
    }

    public ResultCode getResultCode() {
        return resultCode;
    }
}
