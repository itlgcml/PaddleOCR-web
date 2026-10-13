package com.paddleocr.web.service.approval;

import com.paddleocr.web.model.PageResultVO;
import com.paddleocr.web.model.dto.InstanceCancelDTO;
import com.paddleocr.web.model.dto.InstanceStartDTO;
import com.paddleocr.web.model.dto.NextApproverQueryDTO;
import com.paddleocr.web.model.dto.NodeButtonsQueryDTO;
import com.paddleocr.web.model.dto.RuntimePageQuery;
import com.paddleocr.web.model.vo.*;

import java.util.List;

/**
 * 审批实例服务（发起/列表/详情/撤销/预览/单据状态）
 *
 * @author paddleocr
 * @date 2026-10-12
 */
public interface FlowInstanceService {

    /**
     * 可发起流程预检（PUBLISHED 且通过发起范围校验）
     */
    List<StartableProcessVO> startable(Long userId);

    /**
     * 发起审批（单据级发起锁内完成：流程/范围/单据/RUNNING 唯一性校验 → 引擎推进）
     *
     * @return 实例 ID（字符串化，规避前端 Long 精度丢失）
     */
    String start(InstanceStartDTO dto, Long userId);

    /**
     * 我发起的实例分页（status/keyword 过滤）
     */
    PageResultVO<InstanceItemVO> pageMine(RuntimePageQuery query, Long userId);

    /**
     * 实例详情（表单快照 + 画布 JSON + 节点进度 + 审批记录 + 当前用户视角）；非参与人 40059
     */
    InstanceDetailVO detail(Long instanceId, Long userId);

    /**
     * 撤销实例（仅发起人且 RUNNING）：级联取消待办 + 实例终态
     */
    void cancel(Long instanceId, InstanceCancelDTO dto, Long userId);

    /**
     * 下一审批节点预览（dryRun，conditions 真实求值）
     */
    NextApproverVO nextApprovers(NextApproverQueryDTO dto, Long userId);

    /**
     * 节点审批动作按钮预览（流程定义维度，nodeId 缺省=发起后第一个审批节点）
     */
    NodeButtonsVO nodeButtons(NodeButtonsQueryDTO dto, Long userId);

    /**
     * 业务单据审批状态批量查询（列表徽标；每单据取最新实例）
     */
    List<BizApprovalStatusVO> bizStatus(String bizType, List<Long> bizIds);

    /**
     * 运行态用户选项查询（登录即可，供转办/加签/抄送选人；keyword 前缀匹配用户名/昵称，仅启用用户）
     */
    List<UserBriefVO> principalUsers(String keyword, int limit);
}
