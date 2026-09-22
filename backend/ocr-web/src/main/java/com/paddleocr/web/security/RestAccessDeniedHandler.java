package com.paddleocr.web.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paddleocr.web.common.ApiResponse;
import com.paddleocr.web.common.ResultCode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * 403 统一输出（URL 级授权拒绝通路）
 *
 * <p>方法级 @PreAuthorize 拒绝发生在 MVC 层，由 GlobalExceptionHandler 的
 * AccessDeniedException Handler 输出 40300（另一条独立通路）</p>
 *
 * @author paddleocr
 * @date 2026-09-22
 */
@Component
@RequiredArgsConstructor
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), ApiResponse.fail(ResultCode.FORBIDDEN));
    }
}
