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
 * 机构表(树形)
 *
 * @author paddleocr
 * @date 2026-09-22
 */
@Data
@TableName("sys_org")
@Schema(description = "机构表(树形)")
public class OrgDO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "机构 ID（雪花 ID）")
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @Schema(description = "父机构 ID，0=根节点")
    private Long parentId;

    @Schema(description = "祖先链，逗号分隔，如 0,1001,1002", maxLength = 512)
    private String ancestors;

    @Schema(description = "机构编码，业务唯一", maxLength = 32)
    private String orgCode;

    @Schema(description = "机构名称", maxLength = 64)
    private String orgName;

    @Schema(description = "同级排序，越小越靠前")
    private Integer sort;

    @Schema(description = "状态：1=启用 0=停用")
    private Integer status;

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
