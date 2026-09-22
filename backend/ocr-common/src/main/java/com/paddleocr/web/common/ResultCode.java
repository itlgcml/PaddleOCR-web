package com.paddleocr.web.common;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 错误码：0 成功；4xxxx 客户端；5xxxx 系统；503xx OCR 服务段
 */
@Getter
@AllArgsConstructor
public enum ResultCode {

    SUCCESS(0, "成功"),
    PARAM_ERROR(40000, "参数错误"),
    NOT_FOUND(40400, "资源不存在"),
    FILE_TYPE_NOT_ALLOWED(40010, "不支持的文件类型"),
    FILE_TOO_LARGE(40011, "文件大小超出限制"),
    SYSTEM_ERROR(50000, "系统内部错误"),
    DB_ERROR(50001, "数据库操作失败"),
    OCR_SERVICE_UNAVAILABLE(50301, "OCR 识别服务不可用"),
    OCR_SERVICE_TIMEOUT(50302, "OCR 识别超时"),
    OCR_RECOGNIZE_FAILED(50303, "OCR 识别失败");

    private final int code;
    private final String message;
}
