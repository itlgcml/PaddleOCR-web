package com.paddleocr.web.mapper.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 角色表
 *
 * @author paddleocr
 * @date 2026-09-22
 */
@Data
@TableName("sys_role")
@Schema(description = "角色表")
public class RoleDO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "角色 ID（雪花 ID）")
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @Schema(description = "角色编码，程序鉴权用，如 ADMIN / USER", maxLength = 64)
    private String roleCode;

    @Schema(description = "角色名称，展示用", maxLength = 64)
    private String roleName;

    @Schema(description = "角色描述", maxLength = 255)
    private String description;

    @Schema(description = "状态：1=启用 0=停用")
    private Integer status;

    @Schema(description = "内置角色：1=内置禁止修改删除")
    private Integer builtIn;

    @Schema(description = "创建时间")
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @Schema(description = "逻辑删除：0=未删 1=已删")
    @TableLogic
    private Integer deleted;
}
