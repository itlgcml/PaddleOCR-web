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
 * 审批流程实例表
 *
 * @author paddleocr
 * @date 2026-10-12
 */
@Data
@TableName("flow_instance")
@Schema(description = "审批流程实例表")
public class FlowInstanceDO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "实例 ID（雪花 ID）")
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @Schema(description = "流程定义 ID")
    private Long processId;

    @Schema(description = "流程编码(冗余)", maxLength = 64)
    private String processCode;

    @Schema(description = "流程名称(冗余)", maxLength = 64)
    private String processName;

    @Schema(description = "发起时的已发布版本号，如 v2", maxLength = 16)
    private String processVersion;

    @Schema(description = "流程版本 ID，指向 flow_process_version 不可变快照")
    private Long processVersionId;

    @Schema(description = "业务单据类型：LAW_FIRM_SELECT=律所选聘", maxLength = 32)
    private String bizType;

    @Schema(description = "业务单据 ID")
    private Long bizId;

    @Schema(description = "业务标题(发起时冗余快照，列表展示)", maxLength = 128)
    private String bizTitle;

    @Schema(description = "父实例 ID(子流程实例指向父实例；根实例为 NULL)")
    private Long parentInstanceId;

    @Schema(description = "父流程中的子流程节点 ID(SUB_PROCESS 节点 id)", maxLength = 64)
    private String parentNodeId;

    @Schema(description = "流程树根实例 ID(根实例=自身；树级推进锁/级联按此)")
    private Long rootInstanceId;

    @Schema(description = "嵌套深度(根实例=0)")
    private Integer depth;

    @Schema(description = "子流程设计快照(子实例=父版本快照内 SUB_PROCESS 节点的 subDoc；根实例为 NULL 读版本表)")
    private String designJson;

    @Schema(description = "推进模式(子实例冗余创建时的节点 advanceMode：WAIT=父等待完成/IMMEDIATE=父启动即继续；根实例为 NULL)", maxLength = 16)
    private String advanceMode;

    @Schema(description = "发起时表单数据 JSON 快照(条件求值与详情展示)")
    private String formSnapshot;

    @Schema(description = "发起人 ID")
    private Long startUserId;

    @Schema(description = "发起人名称(冗余)", maxLength = 64)
    private String startUserName;

    @Schema(description = "状态：RUNNING=进行中 COMPLETED=已完成 REJECTED=已拒绝 CANCELED=已撤销", maxLength = 16)
    private String status;

    @Schema(description = "结束时间(REJECTED/COMPLETED/CANCELED 时写入)")
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
