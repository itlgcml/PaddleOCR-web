package com.paddleocr.web.client;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.paddleocr.web.client.config.OcrServiceProperties;
import com.paddleocr.web.common.BusinessException;
import com.sun.net.httpserver.HttpServer;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * OcrClient 单元测试：JDK 内置 HttpServer 模拟上游，验证请求契约（file Base64 / fileType / visualize）
 * 与响应处理（信封 errorCode 消化为业务码，result 原样透传）
 *
 * @author paddleocr
 * @date 2026-09-23
 */
class OcrClientTest {

    /** 上游成功响应示例（结构见接口文档 §5，精简保留映射字段） */
    private static final String SUCCESS_BODY = "{"
            + "\"logId\":\"t1\",\"errorCode\":0,\"errorMsg\":\"Success\","
            + "\"result\":{\"layoutParsingResults\":[{"
            + "\"prunedResult\":{\"parsing_res_list\":[{"
            + "\"block_bbox\":[72,68,540,130],\"block_label\":\"title\","
            + "\"block_content\":\"# 文档标题\",\"block_id\":0,\"block_order\":0}]},"
            + "\"markdown\":{\"text\":\"# 文档标题\",\"images\":{}},"
            + "\"inputImage\":\"<Base64>\"}],\"dataInfo\":{}}}";

    private HttpServer server;

    private CloseableHttpClient httpClient;

    private OcrClient ocrClient;

    /** 最近一次请求的路径（断言走 JSON 版 /layout-parsing，而非 form-data 变体） */
    private String lastRequestPath;

    /** 最近一次请求的 Content-Type（断言 JSON 契约） */
    private String lastContentType;

    /** 最近一次请求体文本（断言 file Base64 / fileType / visualize 字段透传） */
    private String lastRequestBody;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
        httpClient = HttpClients.createDefault();
        OcrServiceProperties properties = new OcrServiceProperties();
        properties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        ocrClient = new OcrClient(httpClient, properties);
    }

    @AfterEach
    void tearDown() throws IOException {
        // finally 中关闭资源，禁止 return（EX-06/EX-09）
        try {
            httpClient.close();
        } finally {
            server.stop(0);
        }
    }

    @Test
    void recognizeShouldParseLayoutParsingResult() {
        enqueueHandler(200, SUCCESS_BODY);

        JsonNode result = ocrClient.recognize(pdfFile(), 0);

        // 原样透传：字段名与上游一致（layoutParsingResults[].prunedResult.parsing_res_list[]）
        JsonNode pages = result.get("layoutParsingResults");
        assertEquals(1, pages.size(), "result 应原样携带 layoutParsingResults");
        JsonNode block = pages.get(0).get("prunedResult").get("parsing_res_list").get(0);
        assertEquals(72, block.get("block_bbox").get(0).asInt());
        assertEquals(130, block.get("block_bbox").get(3).asInt());
        assertEquals("title", block.get("block_label").asText());
        assertEquals("# 文档标题", block.get("block_content").asText());
        assertEquals(0, block.get("block_id").asInt());
        assertEquals(0, block.get("block_order").asInt());
        assertEquals("# 文档标题", pages.get(0).get("markdown").get("text").asText());
        assertEquals("<Base64>", pages.get(0).get("inputImage").asText());
        assertTrue(result.has("dataInfo"), "result 应原样携带 dataInfo");
        assertEquals("/layout-parsing", lastRequestPath, "应请求 JSON 版版面解析接口");
        assertTrue(lastContentType.startsWith("application/json"),
                "上游请求应为 application/json");
        JSONObject payload = JSONUtil.parseObj(lastRequestBody);
        assertEquals(Base64.getEncoder()
                        .encodeToString("%PDF-1.4 test".getBytes(StandardCharsets.UTF_8)),
                payload.getStr("file"), "file 应为文件内容 Base64 字符串");
        assertEquals(Integer.valueOf(0), payload.getInt("fileType"), "应透传 fileType");
        assertEquals(Boolean.FALSE, payload.getBool("visualize"), "应通过 visualize=false 关闭可视化结果图（前端按原始文件预览）");
    }

    @Test
    void recognizeShouldFailWhenUpstreamBusinessError() {
        enqueueHandler(200, "{\"logId\":\"t2\",\"errorCode\":400,\"errorMsg\":\"文件解析失败\"}");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> ocrClient.recognize(pdfFile(), 0));

        assertEquals(50303, ex.getCode());
        assertTrue(ex.getMessage().contains("文件解析失败"));
    }

    @Test
    void recognizeShouldFailWhenHttpError() {
        enqueueHandler(500, "internal error");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> ocrClient.recognize(pdfFile(), 1));

        assertEquals(50303, ex.getCode());
    }

    /**
     * 注册模拟上游：捕获请求体并返回指定状态码与响应体
     */
    private void enqueueHandler(int status, String responseBody) {
        server.createContext("/", exchange -> {
            lastRequestPath = exchange.getRequestURI().getPath();
            lastContentType = exchange.getRequestHeaders().getFirst("Content-Type");
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            InputStream in = exchange.getRequestBody();
            byte[] chunk = new byte[1024];
            int read;
            while ((read = in.read(chunk)) != -1) {
                buffer.write(chunk, 0, read);
            }
            lastRequestBody = new String(buffer.toByteArray(), StandardCharsets.UTF_8);
            byte[] out = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, out.length);
            exchange.getResponseBody().write(out);
            exchange.close();
        });
    }

    /**
     * 构造合法 PDF 测试文件（魔数 %PDF）
     */
    private MockMultipartFile pdfFile() {
        return new MockMultipartFile("file", "test.pdf", "application/pdf",
                "%PDF-1.4 test".getBytes(StandardCharsets.UTF_8));
    }
}
