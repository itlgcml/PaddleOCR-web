package com.paddleocr.web.model.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 流程配置列表行 VO
 *
 * @author paddleocr
 * @date 2026-09-30
 */
@Data
@Schema(description = "流程配置列表行")
public class ProcessVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "流程定义 ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @Schema(description = "流程编码")
    private String code;

    @Schema(description = "流程名称")
    private String name;

    @Schema(description = "状态：DRAFT/PUBLISHED/DISABLED")
    private String status;

    @Schema(description = "当前已发布版本号，如 v1；从未发布为 null")
    private String currentVersion;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
