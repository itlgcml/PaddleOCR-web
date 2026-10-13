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
 * 审批记录表
 *
 * @author paddleocr
 * @date 2026-10-12
 */
@Data
@TableName("flow_task_log")
@Schema(description = "审批记录表")
public class FlowTaskLogDO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "记录 ID（雪花 ID）")
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @Schema(description = "实例 ID")
    private Long instanceId;

    @Schema(description = "关联任务 ID(系统动作为 NULL)")
    private Long taskId;

    @Schema(description = "节点 ID", maxLength = 64)
    private String nodeId;

    @Schema(description = "节点名称", maxLength = 64)
    private String nodeName;

    @Schema(description = "动作：START/PASS/REJECT/RETURN/TRANSFER/DELEGATE/ADD_SIGN/SUBMIT/CANCEL/COMMENT/COPY/AUTO_PASS/ENGINE", maxLength = 16)
    private String action;

    @Schema(description = "操作人 ID(引擎动作为 NULL)")
    private Long operatorId;

    @Schema(description = "操作人名称(引擎动作存 系统自动)", maxLength = 64)
    private String operatorName;

    @Schema(description = "意见", maxLength = 512)
    private String comment;

    @Schema(description = "动作摘要：如 退回到【部门审批】；LEADER 降级为 TO_ADMIN 等", maxLength = 512)
    private String detail;

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
