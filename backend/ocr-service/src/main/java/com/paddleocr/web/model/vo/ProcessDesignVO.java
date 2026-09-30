package com.paddleocr.web.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 流程设计查询 VO（前端直接反序列化 designJson 为 WorkflowDoc）
 *
 * @author paddleocr
 * @date 2026-09-30
 */
@Data
@Schema(description = "流程设计查询结果")
public class ProcessDesignVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "版本：查草稿时为 current_version（从未发布为 null）；指定版本查询时为该版本号")
    private String version;

    @Schema(description = "设计类型：DRAFT=草稿 PUBLISHED=版本快照")
    private String status;

    @Schema(description = "流程设计 JSON 字符串（WorkflowDoc）")
    private String designJson;

    @Schema(description = "草稿更新时间 / 版本发布时间")
    private LocalDateTime updateTime;
}
