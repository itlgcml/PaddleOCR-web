package com.paddleocr.web.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 流程版本列表行 VO
 *
 * @author paddleocr
 * @date 2026-09-30
 */
@Data
@Schema(description = "流程版本列表行")
public class ProcessVersionVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "版本号，如 v1/v2/v3")
    private String version;

    @Schema(description = "发布时间")
    private LocalDateTime updateTime;
}
