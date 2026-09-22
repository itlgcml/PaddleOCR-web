package com.paddleocr.web.common;

import lombok.Getter;

/**
 * 业务异常：service/client 层抛出，由 GlobalExceptionHandler 统一转换
 */
@Getter
public class BusinessException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final int code;

    public BusinessException(ResultCode rc) {
        super(rc.getMessage());
        this.code = rc.getCode();
    }

    public BusinessException(ResultCode rc, String detail) {
        super(detail);
        this.code = rc.getCode();
    }
}
