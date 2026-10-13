package com.paddleocr.web.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 可发起流程 VO（发起弹窗预检）
 *
 * @author paddleocr
 * @date 2026-10-12
 */
@Data
@Schema(description = "可发起流程")
public class StartableProcessVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "流程定义 ID")
    private String id;

    @Schema(description = "流程编码")
    private String code;

    @Schema(description = "流程名称")
    private String name;

    @Schema(description = "流程描述")
    private String description;

    @Schema(description = "前端详情路径（发起弹窗\"查看单据\"跳转）")
    private String detailPath;
}
