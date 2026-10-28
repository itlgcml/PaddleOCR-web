package com.paddleocr.web.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 下一节点（普通审批节点）可指定审批人目标（当前任务节点的链后继穿透直通办理节点后为 APPROVER 时，
 * PASS 弹窗展示「下一节点审批人」选择，可指定该节点的审批人）：
 * <ul>
 *     <li>{@code nodeId}/{@code nodeName}：目标审批节点定位与展示</li>
 *     <li>{@code multiMode}：OR=或签 AND=会签，前端据此渲染单选/多选</li>
 *     <li>{@code assigneeOptions}：候选审批人（非空时前端渲染选择；空则按节点默认解析）</li>
 * </ul>
 * 与人工路由（manualRouteOptions）、子流程（subProcessApproverOptions）选项按链后继类型互斥。
 *
 * @see com.paddleocr.web.model.dto.TaskActionDTO#getNextAssignees()
 */
@Data
@Schema(description = "下一节点可指定审批人目标")
public class NextApproverOptionVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "目标审批节点 ID（引擎按该节点登记并消费指定审批人，无需回传 nodeId）")
    private String nodeId;

    @Schema(description = "目标审批节点名称")
    private String nodeName;

    @Schema(description = "多方模式：OR=或签 AND=会签（前端据此渲染单选/多选）")
    private String multiMode;

    @Schema(description = "候选审批人（非空时前端渲染「下一节点审批人」选择，选定值经 nextAssignees 回传）")
    private List<UserBriefVO> assigneeOptions;
}
