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
 * 抄送记录表
 *
 * @author paddleocr
 * @date 2026-10-12
 */
@Data
@TableName("flow_cc")
@Schema(description = "抄送记录表")
public class FlowCcDO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "抄送记录 ID（雪花 ID）")
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @Schema(description = "实例 ID")
    private Long instanceId;

    @Schema(description = "触发抄送的任务 ID(发起时抄送为 NULL)")
    private Long taskId;

    @Schema(description = "接收人 ID")
    private Long receiverId;

    @Schema(description = "接收人名称(冗余)", maxLength = 64)
    private String receiverName;

    @Schema(description = "状态：UNREAD=待阅 READ=已阅", maxLength = 16)
    private String status;

    @Schema(description = "阅读时间")
    private LocalDateTime readTime;

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
