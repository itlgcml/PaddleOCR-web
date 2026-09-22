package com.paddleocr.web.model.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 角色简要信息 VO（用户分页出参内嵌使用）
 *
 * @author paddleocr
 * @date 2026-09-22
 */
@Data
@Schema(description = "角色简要信息")
public class RoleBriefVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "角色 ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @Schema(description = "角色编码")
    private String roleCode;

    @Schema(description = "角色名称")
    private String roleName;
}
