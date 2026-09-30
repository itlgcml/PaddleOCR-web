package com.paddleocr.web.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import java.io.Serializable;
import java.util.List;

/**
 * 流程基本信息更新请求 DTO（code 建后不可改，不提供；仅 DRAFT 态可更新）
 *
 * @author paddleocr
 * @date 2026-09-30
 */
@Data
@Schema(description = "流程基本信息更新请求")
public class ProcessUpdateDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "流程名称", maxLength = 64)
    @NotBlank(message = "流程名称不能为空")
    @Size(max = 64, message = "流程名称不能超过 64 个字符")
    private String name;

    @Schema(description = "前端发起路径，以 / 开头", maxLength = 200)
    @NotBlank(message = "前端发起路径不能为空")
    @Size(max = 200, message = "前端发起路径不能超过 200 个字符")
    @Pattern(regexp = "^/.*$", message = "前端发起路径需以 / 开头")
    private String startPath;

    @Schema(description = "前端详情路径，以 / 开头", maxLength = 200)
    @NotBlank(message = "前端详情路径不能为空")
    @Size(max = 200, message = "前端详情路径不能超过 200 个字符")
    @Pattern(regexp = "^/.*$", message = "前端详情路径需以 / 开头")
    private String detailPath;

    @Schema(description = "流程描述", maxLength = 255)
    @Size(max = 255, message = "流程描述不能超过 255 个字符")
    private String description;

    @Schema(description = "发起范围：ALL=全体 SPECIFIC=指定")
    @NotBlank(message = "发起范围不能为空")
    @Pattern(regexp = "^(ALL|SPECIFIC)$", message = "发起范围仅支持 ALL/SPECIFIC")
    private String initiatorType;

    @Schema(description = "指定人员 ID 集合（SPECIFIC 时与角色并集须非空）")
    private List<String> initiatorMembers;

    @Schema(description = "指定角色 ID 集合（SPECIFIC 时与人员并集须非空）")
    private List<String> initiatorRoles;
}
