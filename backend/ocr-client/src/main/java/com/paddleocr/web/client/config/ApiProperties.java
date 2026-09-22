package com.paddleocr.web.client.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * api 前缀类型安全绑定，禁止 @Value 散落
 */
@Data
@Component
@ConfigurationProperties(prefix = "api")
public class ApiProperties {

    /** 通用 API 响应超时（毫秒） */
    private long timeoutMs = 30000;
}
