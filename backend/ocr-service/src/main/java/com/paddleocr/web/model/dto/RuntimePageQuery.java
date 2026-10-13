package com.paddleocr.web.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 运行态通用分页查询 DTO（我发起的/待办/已办共用）
 *
 * @author paddleocr
 * @date 2026-10-12
 */
@Data
@Schema(description = "运行态通用分页查询")
public class RuntimePageQuery implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "页码（≥1）", example = "1")
    private Integer pageNum;

    @Schema(description = "每页条数（1~100）", example = "10")
    private Integer pageSize;

    @Schema(description = "实例状态过滤：RUNNING/COMPLETED/REJECTED/CANCELED（我发起的列表用）")
    private String status;

    @Schema(description = "关键字，对业务标题/流程名称前缀匹配")
    private String keyword;

    @Schema(description = "动作结果过滤（已办列表用）：PASS/REJECT/RETURN/TRANSFER/DELEGATE/SUBMIT")
    private String action;
}
