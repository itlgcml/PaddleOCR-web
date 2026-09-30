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
 * 审批流程定义表
 *
 * @author paddleocr
 * @date 2026-09-30
 */
@Data
@TableName("flow_process")
@Schema(description = "审批流程定义表")
public class ProcessDO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "流程定义 ID（雪花 ID）")
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @Schema(description = "流程编码，业务唯一", maxLength = 64)
    private String code;

    @Schema(description = "流程名称", maxLength = 64)
    private String name;

    @Schema(description = "流程描述", maxLength = 255)
    private String description;

    @Schema(description = "前端发起路径，以 / 开头", maxLength = 200)
    private String startPath;

    @Schema(description = "前端详情路径，以 / 开头", maxLength = 200)
    private String detailPath;

    @Schema(description = "发起范围：ALL=全体 SPECIFIC=指定", maxLength = 16)
    private String initiatorType;

    @Schema(description = "指定人员 ID(JSON 数组，SPECIFIC 时生效)", maxLength = 512)
    private String initiatorMembers;

    @Schema(description = "指定角色 ID(JSON 数组，SPECIFIC 时生效)", maxLength = 512)
    private String initiatorRoles;

    @Schema(description = "状态：DRAFT=草稿 PUBLISHED=已发布 DISABLED=已停用", maxLength = 16)
    private String status;

    @Schema(description = "当前已发布版本号，如 v1", maxLength = 16)
    private String currentVersion;

    @Schema(description = "草稿流程设计 JSON(WorkflowDoc)，编辑保存区，与发布快照分离")
    private String draftJson;

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
