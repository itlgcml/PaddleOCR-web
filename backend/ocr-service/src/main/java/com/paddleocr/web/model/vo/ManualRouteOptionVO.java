package com.paddleocr.web.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 人工路由选项（ManualRouteOptionVO 镜像）：当前审批节点通过后需人工选择下一步时非空，
 * 列表中每个选项对应 CONDITIONS(routing=MANUAL) / PARALLEL(routing=MANUAL) 节点的一个分支列：
 * <ul>
 *     <li>{@code branchId}：分支列 ID，PASS 时作为 routeTargetId / routeTargetIds 项回传</li>
 *     <li>{@code label}：审批人看到的选项名（条件分支列取 props.label，并行分支列回退列名）</li>
 *     <li>{@code assigneeOptions}：指定审批人目标节点的候选审批人（优先分支列内首个审批节点；
 *     列内无审批节点时回退容器后继链首个审批节点，与引擎流转落点一致；沿链跳过直通办理节点定位；
 *     遇嵌套分支容器为空列表）；非空时前端在选中该分支后渲染可选的「审批人」下拉，
 *     选定值经 PASS 的 routeAssignees 回传覆盖默认解析</li>
 * </ul>
 *
 * @see com.paddleocr.web.model.dto.TaskActionDTO#getRouteAssignees()
 */
@Data
@Schema(description = "人工路由选项")
public class ManualRouteOptionVO {

    @Schema(description = "分支列 ID")
    private String branchId;

    @Schema(description = "选项名称")
    private String label;

    @Schema(description = "指定审批人目标节点的候选审批人（优先列内首个审批节点，空列回退容器后继链首个审批节点；跳过直通办理节点，遇嵌套容器为空）；非空时可在 PASS 时指定该分支的审批人")
    private List<UserBriefVO> assigneeOptions;
}
