package com.paddleocr.web.model.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 流程详情 VO（基本信息 + 发起范围数组 + 当前版本号）
 *
 * @author paddleocr
 * @date 2026-09-30
 */
@Data
@Schema(description = "流程详情")
public class ProcessDetailVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "流程定义 ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @Schema(description = "流程编码")
    private String code;

    @Schema(description = "流程名称")
    private String name;

    @Schema(description = "流程描述")
    private String description;

    @Schema(description = "前端发起路径")
    private String startPath;

    @Schema(description = "前端详情路径")
    private String detailPath;

    @Schema(description = "发起范围：ALL=全体 SPECIFIC=指定")
    private String initiatorType;

    @Schema(description = "指定人员 ID（SPECIFIC 时生效，ALL 时为空数组）")
    private List<String> initiatorMembers;

    @Schema(description = "指定角色 ID（SPECIFIC 时生效，ALL 时为空数组）")
    private List<String> initiatorRoles;

    @Schema(description = "状态：DRAFT/PUBLISHED/DISABLED")
    private String status;

    @Schema(description = "当前已发布版本号，如 v1；从未发布为 null")
    private String currentVersion;
}
