package com.paddleocr.web.controller.advice;

import com.paddleocr.web.common.ApiResponse;
import com.paddleocr.web.common.BusinessException;
import com.paddleocr.web.common.ResultCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ApiResponse<Void> handleBusiness(BusinessException e) {
        log.warn("业务异常: code={}, msg={}", e.getCode(), e.getMessage());
        return ApiResponse.fail(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ApiResponse<Void> handleValid(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + " " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return ApiResponse.fail(ResultCode.PARAM_ERROR.getCode(), msg);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ApiResponse<Void> handleMissingParam(MissingServletRequestParameterException e) {
        return ApiResponse.fail(ResultCode.PARAM_ERROR.getCode(),
                "缺少参数: " + e.getParameterName());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ApiResponse<Void> handleUploadSize(MaxUploadSizeExceededException e) {
        return ApiResponse.fail(ResultCode.FILE_TOO_LARGE);
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public ApiResponse<Void> handleNotFound(NoHandlerFoundException e) {
        return ApiResponse.fail(ResultCode.NOT_FOUND);
    }

    /**
     * 方法级 @PreAuthorize 拒绝（AOP 层异常走 HandlerExceptionResolver）。
     * 必须显式接住，否则被下方 Exception 兜底 Handler 截获吃成 500 SYSTEM_ERROR，
     * 且到不了 RestAccessDeniedHandler（两者是不同通路）。
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException e) {
        log.warn("权限不足: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.fail(ResultCode.FORBIDDEN));
    }

    /**
     * Spring Security 认证异常漏网归一（登录接口内部已翻译的异常不会走到这里）
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthentication(AuthenticationException e) {
        log.warn("认证异常: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.fail(ResultCode.UNAUTHORIZED));
    }

    /**
     * 并发写入撞唯一索引兜底（按约束名映射业务错误码）
     */
    @ExceptionHandler(DuplicateKeyException.class)
    public ApiResponse<Void> handleDuplicateKey(DuplicateKeyException e) {
        String message = e.getMessage() == null ? "" : e.getMessage();
        log.warn("唯一索引冲突: {}", message);
        if (message.contains("uk_username")) {
            return ApiResponse.fail(ResultCode.USERNAME_EXISTS);
        }
        if (message.contains("uk_role_code")) {
            return ApiResponse.fail(ResultCode.ROLE_CODE_EXISTS);
        }
        if (message.contains("uk_org_code")) {
            return ApiResponse.fail(ResultCode.ORG_CODE_EXISTS);
        }
        return ApiResponse.fail(ResultCode.SYSTEM_ERROR);
    }

    @ExceptionHandler(Exception.class)
    public ApiResponse<Void> handleOther(Exception e) {
        log.error("系统异常", e);
        return ApiResponse.fail(ResultCode.SYSTEM_ERROR);
    }
}
