package com.paddleocr.web.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.paddleocr.web.common.ApiResponse;
import com.paddleocr.web.service.OcrRecognizeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * OCR 识别域接口：文件上传识别（图片/PDF）
 *
 * <p>鉴权由 SecurityConfig 既有规则统一纳管（/api/** 默认 authenticated），
 * 登录用户即可调用、不区分角色；本类不加方法级权限注解（项目规约：权限控制统一由 URL 规则实现）</p>
 *
 * @author paddleocr
 * @date 2026-09-23
 */
@Tag(name = "OCR 识别")
@RestController
@RequestMapping("/api/ocr")
@RequiredArgsConstructor
public class OcrController {

    private final OcrRecognizeService ocrRecognizeService;

    /**
     * 文件识别（鉴权）：multipart 流式转发上游，结果直返不落库
     *
     * @param file 上传文件（jpg/jpeg/png/bmp/webp/pdf，≤10MB；缺 part 由既有 Handler 转 40000）
     * @param fileType 文件类型（0=PDF，1=图像；缺 part 由既有 Handler 转 40000）
     * @return 上游 result 原始 JSON（layoutParsingResults / dataInfo，字段名与上游契约一致，不做 VO 映射）
     */
    @Operation(summary = "文件识别（图片/PDF）")
    @PostMapping(value = "/recognize", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<JsonNode> recognize(@RequestParam("file") MultipartFile file,
                                           @RequestParam("fileType") Integer fileType) {
        return ApiResponse.ok(ocrRecognizeService.recognize(file, fileType));
    }
}
