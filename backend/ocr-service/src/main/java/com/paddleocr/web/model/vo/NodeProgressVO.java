package com.paddleocr.web.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 流程节点进度项（实例详情「流程图」页签：按设计快照节点标记走过/进行中/未开始）
 *
 * @author paddleocr
 * @date 2026-10-28
 */
@Data
@Schema(description = "流程节点进度项")
public class NodeProgressVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "节点 ID（WorkflowNode.id）")
    private String nodeId;

    @Schema(description = "节点名称")
    private String nodeName;

    @Schema(description = "节点类型：START/APPROVER/TRANSACT/SUB_PROCESS（分支容器与分支列不下发，前端由列内节点状态聚合）")
    private String nodeType;

    @Schema(description = "节点状态：PASSED=已走过 CURRENT=进行中 REJECTED=已拒绝 CANCELED=已取消 PENDING=未开始")
    private String status;

    @Schema(description = "人员名单：CURRENT=当前待办人（含加签链）；PASSED=历史办理人（系统自动通过为「系统自动」）")
    private List<String> assigneeNames;

    @Schema(description = "最近完成时间（PASSED/REJECTED 时非空）")
    private LocalDateTime finishTime;

    @Schema(description = "子实例 ID（SUB_PROCESS 节点对应最新子流程实例，前端跳转子实例详情；无子实例为 null）")
    private String subInstanceId;
}
