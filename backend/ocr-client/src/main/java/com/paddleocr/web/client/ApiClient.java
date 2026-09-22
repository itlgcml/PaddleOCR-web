package com.paddleocr.web.client;

import com.paddleocr.web.common.BusinessException;
import com.paddleocr.web.common.ResultCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 通用 API 客户端：GET / POST JSON，连接 5s，响应超时由 api.timeout-ms（默认 30 秒）决定
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApiClient {

    private final CloseableHttpClient apiHttpClient;

    /**
     * GET 请求，返回响应体字符串（UTF-8）
     */
    public String get(String url) {
        return execute(url, new HttpGet(url));
    }

    /**
     * POST JSON 请求，返回响应体字符串（UTF-8）
     */
    public String postJson(String url, String json) {
        HttpPost post = new HttpPost(url);
        post.setEntity(new StringEntity(json, ContentType.APPLICATION_JSON));
        return execute(url, post);
    }

    private String execute(String url, HttpUriRequestBase request) {
        HttpClientResponseHandler<String> handler = (ClassicHttpResponse response) -> {
            int code = response.getCode();
            HttpEntity entity = response.getEntity();
            String body = entity == null ? "" : EntityUtils.toString(entity, StandardCharsets.UTF_8);
            if (code != 200) {
                log.warn("API 调用失败, status={}, url={}", code, url);
                throw new BusinessException(ResultCode.SYSTEM_ERROR, "API 返回 " + code);
            }
            return body;
        };
        try {
            return apiHttpClient.execute(request, handler);
        } catch (IOException e) {
            log.error("API 调用异常, url={}", url, e);
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "API 调用异常");
        }
    }
}
