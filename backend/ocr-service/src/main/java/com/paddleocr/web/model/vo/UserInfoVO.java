package com.paddleocr.web.model.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户信息 VO（注册/me/登录出参共用）
 *
 * @author paddleocr
 * @date 2026-09-22
 */
@Data
@Schema(description = "用户信息")
public class UserInfoVO implements Serializable {

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

    @Schema(description = "角色编码集合")
    private List<String> roles;

    @Schema(description = "注册时间")
    private LocalDateTime createTime;
}
