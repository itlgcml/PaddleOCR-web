package com.paddleocr.web.client.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 检测框
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "文本检测框")
public class TextBoxVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "识别文本")
    private String text;

    @Schema(description = "置信度 0-1")
    private Double confidence;

    @Schema(description = "四点坐标（归一化 0-1）")
    private List<PointVO> points;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "坐标点")
    public static class PointVO implements Serializable {

        private static final long serialVersionUID = 1L;

        @Schema(description = "x（归一化 0-1）")
        private Double x;

        @Schema(description = "y（归一化 0-1）")
        private Double y;
    }
}
