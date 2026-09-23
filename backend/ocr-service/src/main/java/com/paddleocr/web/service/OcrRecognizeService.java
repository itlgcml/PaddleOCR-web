package com.paddleocr.web.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.web.multipart.MultipartFile;

/**
 * OCR 识别编排：文件校验 + 委托上游识别
 *
 * @author paddleocr
 * @date 2026-09-23
 */
public interface OcrRecognizeService {

    /**
     * 识别单文件（图片/PDF）：校验通过后流式转发上游，结果直返不落库
     *
     * @param file 上传文件（jpg/jpeg/png/bmp/webp/pdf，非空）
     * @param fileType 文件类型（0=PDF，1=图像）
     * @return 上游 result 原始 JSON 树（layoutParsingResults / dataInfo），本层不做字段映射
     */
    JsonNode recognize(MultipartFile file, Integer fileType);
}
