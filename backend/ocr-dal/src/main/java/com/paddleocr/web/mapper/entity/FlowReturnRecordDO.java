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
 * 审批退回记录表：退回按次落库互不覆盖——DIRECT 记 PENDING 待目标节点完成消费直达
 * （接力退回嵌套直达意图完整保留），STEP 记 FINISHED 纯审计；拒绝退回/实例终态置 RESET
 *
 * @author paddleocr
 * @date 2026-10-26
 */
@Data
@TableName("flow_return_record")
@Schema(description = "审批退回记录表")
public class FlowReturnRecordDO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 状态：待直达（DIRECT 专属，目标节点完成时消费） */
    public static final String STATUS_PENDING = "PENDING";

    /** 状态：已直达（目标节点完成消费，已在 from 节点重建任务） */
    public static final String STATUS_CONSUMED = "CONSUMED";

    /** 状态：未消费重置（拒绝退回按逐级重走 / 实例终态清理悬空记录） */
    public static final String STATUS_RESET = "RESET";

    /** 状态：逐级退回记录（STEP，无直达语义，落库即终态，纯审计留痕） */
    public static final String STATUS_FINISHED = "FINISHED";

    @Schema(description = "记录 ID（雪花 ID）")
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @Schema(description = "实例 ID")
    private Long instanceId;

    @Schema(description = "退回发起任务 ID")
    private Long fromTaskId;

    @Schema(description = "退回发起节点 ID（直达消费后在其上重建任务）", maxLength = 64)
    private String fromNodeId;

    @Schema(description = "退回目标节点 ID（START=根节点=退回发起人）", maxLength = 64)
    private String targetNodeId;

    @Schema(description = "退回方式：STEP=逐级审批 DIRECT=审批后直达本节点", maxLength = 16)
    private String returnMode;

    @Schema(description = "状态：PENDING=待直达 CONSUMED=已直达 RESET=未消费重置 FINISHED=逐级记录", maxLength = 16)
    private String status;

    @Schema(description = "创建时间")
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @Schema(description = "逻辑删除 0=未删 1=已删")
    @TableLogic
    private Integer deleted;
}
