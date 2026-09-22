package com.paddleocr.web.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * springdoc OpenAPI 配置：Bearer 安全校验（swagger-ui 可直接调试鉴权接口）
 *
 * @author paddleocr
 * @date 2026-09-22
 */
@Configuration
public class OpenApiConfig {

    /** Bearer 安全校验名称 */
    private static final String BEARER_SECURITY_NAME = "bearer-key";

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("PaddleOCR-web API")
                        .description("PaddleOCR-web 后端接口（统一前缀 /api，鉴权接口需 Header：Authorization: Bearer <accessToken>）")
                        .version("0.1.0"))
                .components(new Components().addSecuritySchemes(BEARER_SECURITY_NAME,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SECURITY_NAME));
    }
}
