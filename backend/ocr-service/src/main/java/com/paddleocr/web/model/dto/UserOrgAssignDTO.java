package com.paddleocr.web.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotNull;
import java.io.Serializable;

/**
 * 用户机构调整请求 DTO
 *
 * @author paddleocr
 * @date 2026-09-22
 */
@Data
@Schema(description = "用户机构调整请求")
public class UserOrgAssignDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "目标机构 ID（须存在且启用）")
    @NotNull(message = "机构 ID 不能为空")
    private Long orgId;
}
