package com.paddleocr.web.client.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * ocr.service 前缀类型安全绑定，禁止 @Value 散落
 */
@Data
@Component
@ConfigurationProperties(prefix = "ocr.service")
public class OcrServiceProperties {

    private String baseUrl;

    private long timeoutMs = 600000;
}
