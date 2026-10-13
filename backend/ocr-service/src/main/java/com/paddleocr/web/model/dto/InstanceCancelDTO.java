package com.paddleocr.web.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.Size;
import java.io.Serializable;

/**
 * 撤销审批请求 DTO
 *
 * @author paddleocr
 * @date 2026-10-12
 */
@Data
@Schema(description = "撤销审批请求")
public class InstanceCancelDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "撤销原因（CANCEL 日志）", maxLength = 512)
    @Size(max = 512, message = "撤销原因不能超过 512 个字符")
    private String comment;
}
