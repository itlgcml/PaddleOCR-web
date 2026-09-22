package com.paddleocr.web.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paddleocr.web.common.ApiResponse;
import com.paddleocr.web.common.ResultCode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * 401 统一输出：链尾未认证回调（40100）与 Filter 手动接管（40101/40102/40103/40023）
 *
 * <p>必须用全局 ObjectMapper 序列化（保证时间格式一致），禁止手写拼接 JSON</p>
 *
 * @author paddleocr
 * @date 2026-09-22
 */
@Component
@RequiredArgsConstructor
public class RestAuthEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        // 优先读取 AuthFailureException 携带的错误码，缺省输出 40100
        ResultCode resultCode = authException instanceof AuthFailureException
                ? ((AuthFailureException) authException).getResultCode()
                : ResultCode.UNAUTHORIZED;
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), ApiResponse.fail(resultCode));
    }
}
