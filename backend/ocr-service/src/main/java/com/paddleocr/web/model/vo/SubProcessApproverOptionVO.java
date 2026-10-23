package com.paddleocr.web.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 子流程下一节点可指定审批人目标（下一节点为子流程「启动后继续」时，PASS 弹窗分开展示
 * 主流程与子流程审批人，并可分别指定）：
 * <ul>
 *     <li>{@code nodeId}：目标审批节点 ID，PASS 时作为 subProcessAssignees 的 key 回传</li>
 *     <li>{@code scopeType}/{@code scopeName}：归属（MAIN=主流程 / SUB_PROCESS=子流程）+ 流程名，
 *     前端据此区分并标注「主流程「名」· 节点名」/「子流程「名」· 节点名」</li>
 *     <li>{@code assigneeOptions}：候选审批人（非空时前端渲染指定审批人下拉；空则按节点默认解析）</li>
 * </ul>
 *
 * @see com.paddleocr.web.model.dto.TaskActionDTO#getSubProcessAssignees()
 */
@Data
@Schema(description = "子流程下一节点可指定审批人目标")
public class SubProcessApproverOptionVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "目标审批节点 ID（PASS 时作为 subProcessAssignees 的 key 回传）")
    private String nodeId;

    @Schema(description = "目标审批节点名称")
    private String nodeName;

    @Schema(description = "归属：MAIN=主流程 SUB_PROCESS=子流程")
    private String scopeType;

    @Schema(description = "归属流程名称（主流程名/子流程节点名）")
    private String scopeName;

    @Schema(description = "候选审批人（非空时前端渲染指定审批人下拉，空则按节点默认解析）")
    private List<UserBriefVO> assigneeOptions;
}
