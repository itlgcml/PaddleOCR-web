package com.paddleocr.web.controller;

import com.paddleocr.web.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 健康检查
 */
@Tag(name = "健康检查")
@RestController
@RequestMapping("/api/health")
public class HealthController {

    @Operation(summary = "服务健康检查")
    @GetMapping
    public ApiResponse<String> health() {
        return ApiResponse.ok("UP");
    }
}
