package com.paddleocr.web.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.Digits;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 律所选聘新增/修改请求 DTO（全字段可改，新增修改共用）
 *
 * @author paddleocr
 * @date 2026-09-30
 */
@Data
@Schema(description = "律所选聘新增/修改请求")
public class LawFirmSelectSaveDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "标题", maxLength = 128)
    @NotBlank(message = "标题不能为空")
    @Size(min = 1, max = 128, message = "标题长度须为 1~128 位")
    private String title;

    @Schema(description = "选聘金额（元），最多两位小数")
    @NotNull(message = "选聘金额不能为空")
    @DecimalMin(value = "0.01", message = "选聘金额须大于 0")
    @Digits(integer = 10, fraction = 2, message = "选聘金额整数部分不超过 10 位、小数不超过 2 位")
    private BigDecimal amount;

    @Schema(description = "选聘原因", maxLength = 512)
    @NotBlank(message = "选聘原因不能为空")
    @Size(min = 1, max = 512, message = "选聘原因长度须为 1~512 位")
    private String reason;
}
