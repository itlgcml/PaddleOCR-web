package com.paddleocr.web.mapper.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 系统用户表(员工)
 *
 * @author paddleocr
 * @date 2026-09-22
 */
@Data
@TableName("sys_user")
@Schema(description = "系统用户表(员工)")
public class UserDO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "用户 ID（雪花 ID）")
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @Schema(description = "所属机构 ID（1:n 机构方持有），NULL=未分配")
    private Long orgId;

    @Schema(description = "用户名，登录用", maxLength = 32)
    private String username;

    @Schema(description = "昵称", maxLength = 64)
    private String nickname;

    @Schema(description = "邮箱", maxLength = 128)
    private String email;

    @Schema(description = "密码（BCrypt 密文，禁止明文落库）", maxLength = 60)
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String passwordHash;

    @Schema(description = "状态：1=启用 0=禁用")
    private Integer status;

    @Schema(description = "连续登录失败次数")
    private Integer failedAttempts;

    @Schema(description = "锁定到期时间")
    private LocalDateTime lockedUntil;

    @Schema(description = "最近登录时间")
    private LocalDateTime lastLoginTime;

    @Schema(description = "最近登录 IP", maxLength = 64)
    private String lastLoginIp;

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
