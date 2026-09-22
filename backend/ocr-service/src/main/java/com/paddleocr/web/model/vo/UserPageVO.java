package com.paddleocr.web.model.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户分页记录 VO
 *
 * @author paddleocr
 * @date 2026-09-22
 */
@Data
@Schema(description = "用户分页记录")
public class UserPageVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "用户 ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @Schema(description = "用户名")
    private String username;

    @Schema(description = "昵称")
    private String nickname;

    @Schema(description = "邮箱")
    private String email;

    @Schema(description = "所属机构（未分配时为 null）")
    private OrgBriefVO org;

    @Schema(description = "角色列表")
    private List<RoleBriefVO> roles;

    @Schema(description = "状态：1=启用 0=禁用")
    private Integer status;

    @Schema(description = "最近登录时间")
    private LocalDateTime lastLoginTime;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
