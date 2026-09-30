package com.paddleocr.web.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import java.io.Serializable;

/**
 * 流程配置分页查询参数
 *
 * @author paddleocr
 * @date 2026-09-30
 */
@Data
@Schema(description = "流程配置分页查询参数")
public class ProcessPageQuery implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "页码（默认 1）")
    private Integer pageNum;

    @Schema(description = "每页条数（默认 10，1~100）")
    private Integer pageSize;

    @Schema(description = "按流程编码/名称前缀匹配", maxLength = 64)
    @Size(max = 64, message = "关键字不能超过 64 个字符")
    private String keyword;

    @Schema(description = "状态精确过滤：DRAFT/PUBLISHED/DISABLED")
    @Pattern(regexp = "^(DRAFT|PUBLISHED|DISABLED)$", message = "状态仅支持 DRAFT/PUBLISHED/DISABLED")
    private String status;
}
