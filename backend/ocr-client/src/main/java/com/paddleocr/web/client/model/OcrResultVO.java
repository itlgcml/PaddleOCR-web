package com.paddleocr.web.client.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 识别结果
 */
@Data
@Schema(description = "OCR 识别结果")
public class OcrResultVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "全量文本（换行拼接）")
    private String fullText;

    @Schema(description = "文本检测框列表")
    private List<TextBoxVO> textBoxes;

    @Schema(description = "识别耗时（ms）")
    private Long costMs;

    @Schema(description = "识别记录 ID（字符串雪花 ID）")
    private String recordId;
}
