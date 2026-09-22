package com.paddleocr.web.common;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

@Data
@Schema(description = "统一响应体")
public class ApiResponse<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "0=成功，非 0=失败")
    private int code;

    @Schema(description = "提示信息")
    private String message;

    @Schema(description = "业务数据")
    private T data;

    @Schema(description = "服务端时间戳(ms)")
    private long timestamp;

    public static <T> ApiResponse<T> ok(T data) {
        ApiResponse<T> r = new ApiResponse<T>();
        r.code = ResultCode.SUCCESS.getCode();
        r.message = ResultCode.SUCCESS.getMessage();
        r.data = data;
        r.timestamp = System.currentTimeMillis();
        return r;
    }

    public static ApiResponse<Void> ok() {
        return ok(null);
    }

    public static <T> ApiResponse<T> fail(ResultCode rc) {
        ApiResponse<T> r = new ApiResponse<T>();
        r.code = rc.getCode();
        r.message = rc.getMessage();
        r.timestamp = System.currentTimeMillis();
        return r;
    }

    public static <T> ApiResponse<T> fail(int code, String message) {
        ApiResponse<T> r = new ApiResponse<T>();
        r.code = code;
        r.message = message;
        r.timestamp = System.currentTimeMillis();
        return r;
    }
}
