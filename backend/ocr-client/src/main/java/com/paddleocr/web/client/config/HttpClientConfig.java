package com.paddleocr.web.client.config;

import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.util.Timeout;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * HTTP 客户端统一配置：单例连接池，连接超时统一 5s，keep-alive 30s
 * <ul>
 *   <li>ocrHttpClient：OCR 推理专用，响应超时由 ocr.service.timeout-ms（默认 10 分钟）决定</li>
 *   <li>apiHttpClient：通用 API 调用，响应超时由 api.timeout-ms（默认 30 秒）决定</li>
 * </ul>
 * 两客户端独立连接池，长耗时的 OCR 请求不挤占通用 API 连接
 */
@Configuration
public class HttpClientConfig {

    @Bean(destroyMethod = "close")
    public CloseableHttpClient ocrHttpClient() {
        PoolingHttpClientConnectionManager cm = PoolingHttpClientConnectionManagerBuilder.create()
                .setMaxConnTotal(20)
                .setMaxConnPerRoute(10)
                .setDefaultConnectionConfig(ConnectionConfig.custom()
                        .setConnectTimeout(Timeout.ofSeconds(5))
                        .build())
                .build();
        return HttpClients.custom()
                .setConnectionManager(cm)
                .setKeepAliveStrategy((response, context) -> Timeout.ofSeconds(30))
                .build();
    }

    @Bean(destroyMethod = "close")
    public CloseableHttpClient apiHttpClient(ApiProperties props) {
        PoolingHttpClientConnectionManager cm = PoolingHttpClientConnectionManagerBuilder.create()
                .setMaxConnTotal(20)
                .setMaxConnPerRoute(10)
                .setDefaultConnectionConfig(ConnectionConfig.custom()
                        .setConnectTimeout(Timeout.ofSeconds(5))
                        .build())
                .build();
        return HttpClients.custom()
                .setConnectionManager(cm)
                .setDefaultRequestConfig(RequestConfig.custom()
                        .setResponseTimeout(Timeout.ofMilliseconds(props.getTimeoutMs()))
                        .build())
                .setKeepAliveStrategy((response, context) -> Timeout.ofSeconds(30))
                .build();
    }
}
