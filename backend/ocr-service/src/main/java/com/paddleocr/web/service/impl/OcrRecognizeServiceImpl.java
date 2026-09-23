package com.paddleocr.web.service.impl;

import cn.hutool.core.io.FileTypeUtil;
import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.paddleocr.web.client.OcrClient;
import com.paddleocr.web.common.BusinessException;
import com.paddleocr.web.common.ResultCode;
import com.paddleocr.web.service.OcrRecognizeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * OCR 识别编排实现：三重文件校验 + 委托 OcrClient
 *
 * <p>无 DB 操作，不加 @Transactional（事务方法内禁止 HTTP 调用，OCR 推理最长 10 分钟）</p>
 *
 * @author paddleocr
 * @date 2026-09-23
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OcrRecognizeServiceImpl implements OcrRecognizeService {

    /** 扩展名白名单（小写） */
    private static final Set<String> ALLOWED_EXTENSIONS = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList("jpg", "jpeg", "png", "bmp", "webp", "pdf")));

    /** 文件类型：PDF */
    private static final int FILE_TYPE_PDF = 0;

    /** 文件类型：图像 */
    private static final int FILE_TYPE_IMAGE = 1;

    /** ContentType 白名单（小写） */
    private static final Set<String> ALLOWED_CONTENT_TYPES = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList("image/jpeg", "image/png", "image/bmp", "image/webp", "application/pdf")));

    /** 扩展名 → FileTypeUtil 魔数识别名映射（webp 为 RIFF 容器特例，走手工前缀校验，不在此表） */
    private static final Map<String, String> EXTENSION_MAGIC_TYPE;

    /** webp 魔数头长度：RIFF(4) + 文件大小(4) + WEBP(4) */
    private static final int WEBP_HEADER_LENGTH = 12;

    static {
        Map<String, String> mapping = new HashMap<>(8);
        mapping.put("jpg", "jpg");
        mapping.put("jpeg", "jpg");
        mapping.put("png", "png");
        mapping.put("bmp", "bmp");
        mapping.put("pdf", "pdf");
        EXTENSION_MAGIC_TYPE = Collections.unmodifiableMap(mapping);
    }

    private final OcrClient ocrClient;

    @Override
    public JsonNode recognize(MultipartFile file, Integer fileType) {
        validateFileType(fileType);
        validateFile(file);
        long start = System.currentTimeMillis();
        JsonNode result = ocrClient.recognize(file, fileType);
        log.info("文件识别完成, fileName={}, fileType={}, fileSize={}, totalCostMs={}",
                file.getOriginalFilename(), fileType, file.getSize(), System.currentTimeMillis() - start);
        return result;
    }

    /**
     * 文件类型值域校验：0=PDF，1=图像；缺失或越界 → 40000
     */
    private void validateFileType(Integer fileType) {
        if (fileType == null || (fileType != FILE_TYPE_PDF && fileType != FILE_TYPE_IMAGE)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "fileType 仅支持 0（PDF）或 1（图像）");
        }
    }

    /**
     * 三重校验，快速失败：空内容 → 40000；扩展名 / ContentType / 魔数任一不过 → 40010
     */
    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "上传文件不能为空");
        }
        String filename = file.getOriginalFilename();
        if (StrUtil.isBlank(filename)) {
            throw new BusinessException(ResultCode.FILE_TYPE_NOT_ALLOWED);
        }
        String extension = StrUtil.subAfter(filename, '.', true).toLowerCase(Locale.ROOT);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new BusinessException(ResultCode.FILE_TYPE_NOT_ALLOWED);
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase(Locale.ROOT))) {
            throw new BusinessException(ResultCode.FILE_TYPE_NOT_ALLOWED);
        }
        checkMagicNumber(file, extension);
    }

    /**
     * 魔数校验：防"改扩展名伪装"绕过（如可执行文件改名 pdf）
     */
    private void checkMagicNumber(MultipartFile file, String extension) {
        // webp 特例：RIFF 容器（头 4 字节 RIFF + 第 9-12 字节 WEBP），FileTypeUtil 不内置识别
        if ("webp".equals(extension)) {
            checkWebpMagic(file);
            return;
        }
        String magicType;
        try (InputStream in = file.getInputStream()) {
            // 只按魔数识别，不传文件名：禁止 FileTypeUtil 的"识别失败按扩展名兜底"行为，防伪装文件绕过
            magicType = FileTypeUtil.getType(in);
        } catch (IOException e) {
            throw new BusinessException(ResultCode.OCR_RECOGNIZE_FAILED, "读取上传文件失败");
        }
        if (!EXTENSION_MAGIC_TYPE.getOrDefault(extension, "").equals(magicType)) {
            throw new BusinessException(ResultCode.FILE_TYPE_NOT_ALLOWED);
        }
    }

    /**
     * webp 魔数手工前缀校验：RIFF????WEBP
     */
    private void checkWebpMagic(MultipartFile file) {
        byte[] head = new byte[WEBP_HEADER_LENGTH];
        try (InputStream in = file.getInputStream()) {
            if (in.read(head) < WEBP_HEADER_LENGTH
                    || head[0] != 'R' || head[1] != 'I' || head[2] != 'F' || head[3] != 'F'
                    || head[8] != 'W' || head[9] != 'E' || head[10] != 'B' || head[11] != 'P') {
                throw new BusinessException(ResultCode.FILE_TYPE_NOT_ALLOWED);
            }
        } catch (IOException e) {
            throw new BusinessException(ResultCode.OCR_RECOGNIZE_FAILED, "读取上传文件失败");
        }
    }
}
