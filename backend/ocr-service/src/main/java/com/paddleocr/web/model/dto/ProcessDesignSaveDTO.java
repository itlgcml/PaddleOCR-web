package com.paddleocr.web.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import java.io.Serializable;

/**
 * 流程设计保存请求 DTO（长度上限为可配置值，由 Service 层编程式校验）
 *
 * @author paddleocr
 * @date 2026-09-30
 */
@Data
@Schema(description = "流程设计保存请求")
public class ProcessDesignSaveDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "流程设计 JSON 字符串（WorkflowDoc 完整结构，上限默认 512KB）")
    @NotBlank(message = "流程设计内容不能为空")
    private String designJson;
}
