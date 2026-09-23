package com.paddleocr.web.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paddleocr.web.client.config.OcrServiceProperties;
import com.paddleocr.web.client.constant.OcrApi;
import com.paddleocr.web.client.model.LayoutParsingRequest;
import com.paddleocr.web.common.BusinessException;
import com.paddleocr.web.common.ResultCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.ConnectTimeoutException;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.util.Timeout;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * OCR 识别客户端：JSON 转发至上游版面解析服务 /layout-parsing（图片/PDF，file 字段为 Base64 字符串）
 *
 * <p>走专用 ocrHttpClient 连接池（总 20 / 每路由 10，连接 5s，keep-alive 30s）；
 * 响应超时按请求级设置（ocr.service.timeout-ms，默认 600 秒），补齐池级未配置
 * responseTimeout 的缺口；上游异常统一转换为 503xx 业务码，不向 service/web 层泄漏协议细节；
 * 日志禁止打印文件字节与响应原文</p>
 *
 * <p>出参不做 VO 映射：信封 {logId, errorCode, errorMsg, result} 由本类消化为业务码，
 * 仅 result 以 JsonNode 原样透传给前端（字段名与上游契约一致，见接口文档 §3）</p>
 *
 * @author paddleocr
 * @date 2026-09-23
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OcrClient {

    private static final int HTTP_OK = 200;

    /** 上游业务成功码（信封 errorCode） */
    private static final int UPSTREAM_SUCCESS = 0;

    /** JSON 读写（Jackson 本身线程安全，读树 + 序列化请求体共用） */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final CloseableHttpClient ocrHttpClient;

    private final OcrServiceProperties ocrServiceProperties;

    /**
     * 文件识别（图片/PDF）：MultipartFile 全量读入并 Base64 编码，随 JSON body 上送（受 100MB 上传上限约束，注意大文件内存占用）
     *
     * @param file 上传文件（经 service 层三重校验，非空且类型合法）
     * @param fileType 文件类型（0=PDF，1=图像，经 service 层值域校验）
     * @return 上游 result 原始 JSON 树（layoutParsingResults / dataInfo，字段名与上游一致）
     */
    public JsonNode recognize(MultipartFile file, Integer fileType) {
        // JSON 契约走 /layout-parsing（form-data 变体只收 multipart，不能用 JSON 调）
        String layoutParsingUrl = ocrServiceProperties.getBaseUrl() + OcrApi.LAYOUT_PARSING.getPath();
        HttpPost post = new HttpPost(layoutParsingUrl);
        // 请求级响应超时（不改共享池 Bean，按接口独立演进，避免 600s 默认值绑架短超时接口）
        post.setConfig(RequestConfig.custom()
                .setResponseTimeout(Timeout.ofMilliseconds(ocrServiceProperties.getTimeoutMs()))
                .build());
        // JSON body：file 为文件内容 Base64 字符串，fileType 显式声明（0=PDF，1=图像），visualize=false 关闭结果图返回（前端按原始文件预览），
        // useChartRecognition/useSealRecognition 固定 true 开启图表识别与印章识别（请求体字段默认值随序列化上送）
        LayoutParsingRequest requestBody = new LayoutParsingRequest(encodeBase64(file), fileType);
        post.setEntity(new StringEntity(toJson(requestBody), ContentType.APPLICATION_JSON));

        HttpClientResponseHandler<JsonNode> handler = (ClassicHttpResponse response) -> {
            int code = response.getCode();
            HttpEntity entity = response.getEntity();
            String body = entity == null ? "" : EntityUtils.toString(entity, StandardCharsets.UTF_8);
            if (code != HTTP_OK) {
                log.warn("OCR 上游返回异常状态, status={}, url={}", code, layoutParsingUrl);
                // 上游错误体不透传（可能含内部信息），仅带状态码
                throw new BusinessException(ResultCode.OCR_RECOGNIZE_FAILED, "OCR 上游返回 " + code);
            }
            return parseResult(body);
        };
        try {
            return ocrHttpClient.execute(post, handler);
        } catch (ConnectException | ConnectTimeoutException e) {
            // TCP 拒绝（含 client5 包装的 HttpHostConnectException）与连接建立超时，均归服务不可用
            log.error("OCR 服务连接失败或超时, url={}", layoutParsingUrl, e);
            throw new BusinessException(ResultCode.OCR_SERVICE_UNAVAILABLE);
        } catch (SocketTimeoutException e) {
            // classic 5.x：responseTimeout 映射底层 socket 读超时（SocketTimeoutException），归识别超时
            log.error("OCR 服务响应超时, url={}, timeoutMs={}", layoutParsingUrl, ocrServiceProperties.getTimeoutMs());
            throw new BusinessException(ResultCode.OCR_SERVICE_TIMEOUT);
        } catch (IOException e) {
            log.error("OCR 服务调用异常, url={}", layoutParsingUrl, e);
            throw new BusinessException(ResultCode.OCR_RECOGNIZE_FAILED, "OCR 服务 IO 异常");
        }
    }

    /**
     * 解析上游响应：信封 {logId, errorCode, errorMsg, result} 消化为业务码，result 原样透传（契约见接口文档 §3）
     */
    private JsonNode parseResult(String body) {
        try {
            JsonNode envelope = OBJECT_MAPPER.readTree(body);
            JsonNode errorCodeNode = envelope.get("errorCode");
            if (errorCodeNode == null || !errorCodeNode.isNumber()
                    || errorCodeNode.asInt() != UPSTREAM_SUCCESS) {
                // 上游错误体不透传（可能含内部信息），仅带 errorMsg
                log.warn("OCR 上游返回业务错误, errorCode={}", errorCodeNode);
                throw new BusinessException(ResultCode.OCR_RECOGNIZE_FAILED,
                        "OCR 上游识别失败: " + envelope.path("errorMsg").asText());
            }
            JsonNode result = envelope.get("result");
            if (result == null || result.isNull()) {
                throw new BusinessException(ResultCode.OCR_RECOGNIZE_FAILED, "OCR 响应缺少结果体");
            }
            return result;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            // 日志红线：不打响应原文（可能为大文本），仅记长度
            log.error("OCR 响应解析失败, bodyLength={}", body == null ? 0 : body.length(), e);
            throw new BusinessException(ResultCode.OCR_RECOGNIZE_FAILED, "OCR 响应解析失败");
        }
    }

    /**
     * 请求体序列化（LayoutParsingRequest → JSON，字段名与上游契约一致）
     */
    private String toJson(LayoutParsingRequest request) {
        try {
            return OBJECT_MAPPER.writeValueAsString(request);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ResultCode.OCR_RECOGNIZE_FAILED, "OCR 请求体序列化失败");
        }
    }

    /**
     * 读取上传文件并 Base64 编码（上游 JSON 契约：file 字段为 Base64 字符串，无 data: 前缀）
     */
    private String encodeBase64(MultipartFile file) {
        try {
            return Base64.getEncoder().encodeToString(file.getBytes());
        } catch (IOException e) {
            throw new BusinessException(ResultCode.OCR_RECOGNIZE_FAILED, "读取上传文件失败");
        }
    }
}
