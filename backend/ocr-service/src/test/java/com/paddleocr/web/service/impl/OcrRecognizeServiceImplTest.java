package com.paddleocr.web.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paddleocr.web.client.OcrClient;
import com.paddleocr.web.common.BusinessException;
import com.paddleocr.web.service.OcrRecognizeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * OcrRecognizeServiceImpl 单元测试：fileType 值域校验 + 三重文件校验 + 委托透传
 *
 * @author paddleocr
 * @date 2026-09-23
 */
class OcrRecognizeServiceImplTest {

    /** 上游 result 原始 JSON（透传不做映射，服务层仅原样返回） */
    private static final JsonNode RAW_RESULT = new ObjectMapper()
            .createObjectNode()
            .put("dataInfo", "upstream");

    private OcrClient ocrClient;

    private OcrRecognizeService ocrRecognizeService;

    @BeforeEach
    void setUp() {
        ocrClient = Mockito.mock(OcrClient.class);
        ocrRecognizeService = new OcrRecognizeServiceImpl(ocrClient);
    }

    @Test
    void recognizeShouldRejectMissingFileType() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> ocrRecognizeService.recognize(pdfFile(), null));

        assertEquals(40000, ex.getCode());
        assertTrue(ex.getMessage().contains("fileType"));
        verifyNoInteractions(ocrClient);
    }

    @Test
    void recognizeShouldRejectInvalidFileType() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> ocrRecognizeService.recognize(pdfFile(), 2));

        assertEquals(40000, ex.getCode());
        verifyNoInteractions(ocrClient);
    }

    @Test
    void recognizeShouldRejectEmptyFile() {
        MockMultipartFile file = new MockMultipartFile("file", "test.pdf", "application/pdf", new byte[0]);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> ocrRecognizeService.recognize(file, 0));

        assertEquals(40000, ex.getCode());
        verifyNoInteractions(ocrClient);
    }

    @Test
    void recognizeShouldRejectBadExtension() {
        MockMultipartFile file = new MockMultipartFile("file", "test.txt", "text/plain",
                "hello".getBytes(StandardCharsets.UTF_8));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> ocrRecognizeService.recognize(file, 1));

        assertEquals(40010, ex.getCode());
        verifyNoInteractions(ocrClient);
    }

    @Test
    void recognizeShouldReturnClientResultForPdf() {
        MockMultipartFile file = pdfFile();
        JsonNode raw = RAW_RESULT.deepCopy();
        when(ocrClient.recognize(file, 0)).thenReturn(raw);

        assertSame(raw, ocrRecognizeService.recognize(file, 0));
        verify(ocrClient).recognize(file, 0);
    }

    @Test
    void recognizeShouldReturnClientResultForImage() {
        MockMultipartFile file = new MockMultipartFile("file", "test.png", "image/png",
                new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A});
        JsonNode raw = RAW_RESULT.deepCopy();
        when(ocrClient.recognize(file, 1)).thenReturn(raw);

        assertSame(raw, ocrRecognizeService.recognize(file, 1));
        verify(ocrClient).recognize(file, 1);
    }

    /**
     * 构造合法 PDF 测试文件（魔数 %PDF）
     */
    private MockMultipartFile pdfFile() {
        return new MockMultipartFile("file", "test.pdf", "application/pdf",
                "%PDF-1.4 test".getBytes(StandardCharsets.UTF_8));
    }
}
