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
 * 审批任务表
 *
 * @author paddleocr
 * @date 2026-10-12
 */
@Data
@TableName("flow_task")
@Schema(description = "审批任务表")
public class FlowTaskDO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "任务 ID（雪花 ID）")
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @Schema(description = "实例 ID")
    private Long instanceId;

    @Schema(description = "节点 ID(WorkflowNode.id)", maxLength = 64)
    private String nodeId;

    @Schema(description = "节点名称(冗余)", maxLength = 64)
    private String nodeName;

    @Schema(description = "审批人用户 ID")
    private Long assigneeId;

    @Schema(description = "审批人名称(冗余)", maxLength = 64)
    private String assigneeName;

    @Schema(description = "多实例模式快照：OR=或签 AND=会签", maxLength = 8)
    private String multiMode;

    @Schema(description = "任务派生类型：NORMAL=普通 BEFORE_SIGN/AFTER_SIGN=前/后加签 TRANSFER=转办 DELEGATE=委托", maxLength = 16)
    private String signType;

    @Schema(description = "派生源任务 ID：加签任务挂源任务之下（转办/委托新任务为 NULL，链路由日志关联），根任务为 NULL")
    private Long parentTaskId;

    @Schema(description = "状态：PENDING=待办 PASSED=通过 REJECTED=已拒绝 CANCELED=已取消 TRANSFERRED=已转出", maxLength = 16)
    private String status;

    @Schema(description = "完成动作：PASS/REJECT/RETURN/TRANSFER/DELEGATE/SUBMIT，PENDING 为 NULL", maxLength = 16)
    private String action;

    @Schema(description = "审批意见(任务完成时冗余最近一次)", maxLength = 512)
    private String comment;

    @Schema(description = "完成时间")
    private LocalDateTime finishTime;

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
