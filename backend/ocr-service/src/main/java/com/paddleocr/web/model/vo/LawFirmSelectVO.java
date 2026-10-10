package com.paddleocr.web.model.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 律所选聘信息 VO
 *
 * @author paddleocr
 * @date 2026-09-30
 */
@Data
@Schema(description = "律所选聘信息")
public class LawFirmSelectVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "律所选聘 ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @Schema(description = "标题")
    private String title;

    @Schema(description = "选聘金额（元）")
    private BigDecimal amount;

    @Schema(description = "选聘原因")
    private String reason;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
