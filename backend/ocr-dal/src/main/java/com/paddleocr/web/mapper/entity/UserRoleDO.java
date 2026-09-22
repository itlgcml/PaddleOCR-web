package com.paddleocr.web.mapper.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户角色关联表(n:m)
 *
 * <p>例外约定：无 deleted（物理删除）、无 updateTime（无更新语义）、createTime 仅 INSERT 填充</p>
 *
 * @author paddleocr
 * @date 2026-09-22
 */
@Data
@TableName("sys_user_role")
@Schema(description = "用户角色关联表(n:m)")
public class UserRoleDO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "主键 ID（雪花 ID）")
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @Schema(description = "用户 ID")
    private Long userId;

    @Schema(description = "角色 ID")
    private Long roleId;

    @Schema(description = "绑定时间")
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
