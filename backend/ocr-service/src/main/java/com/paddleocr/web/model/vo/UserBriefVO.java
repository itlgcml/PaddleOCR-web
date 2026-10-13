package com.paddleocr.web.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 用户简要信息 VO（审批人/抄送人展示）
 *
 * @author paddleocr
 * @date 2026-10-12
 */
@Data
@Schema(description = "用户简要信息")
public class UserBriefVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "用户 ID")
    private String userId;

    @Schema(description = "用户名称")
    private String name;
}
