package com.paddleocr.web.model.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 角色信息 VO
 *
 * @author paddleocr
 * @date 2026-09-22
 */
@Data
@Schema(description = "角色信息")
public class RoleVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "角色 ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @Schema(description = "角色编码")
    private String roleCode;

    @Schema(description = "角色名称")
    private String roleName;

    @Schema(description = "角色描述")
    private String description;

    @Schema(description = "状态：1=启用 0=停用")
    private Integer status;

    @Schema(description = "内置角色：1=内置（ADMIN/USER），前端隐藏编辑/删除按钮")
    private Integer builtIn;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
