package com.paddleocr.web.controller.approval;

import com.paddleocr.web.annotation.RepeatSubmit;
import com.paddleocr.web.common.ApiResponse;
import com.paddleocr.web.common.BusinessException;
import com.paddleocr.web.common.ResultCode;
import com.paddleocr.web.model.PageResultVO;
import com.paddleocr.web.model.dto.InstanceCancelDTO;
import com.paddleocr.web.model.dto.InstanceStartDTO;
import com.paddleocr.web.model.dto.NextApproverQueryDTO;
import com.paddleocr.web.model.dto.NodeButtonsQueryDTO;
import com.paddleocr.web.model.dto.RuntimePageQuery;
import com.paddleocr.web.model.vo.BizApprovalStatusVO;
import com.paddleocr.web.model.vo.InstanceDetailVO;
import com.paddleocr.web.model.vo.InstanceItemVO;
import com.paddleocr.web.model.vo.NextApproverVO;
import com.paddleocr.web.model.vo.NodeButtonsVO;
import com.paddleocr.web.model.vo.StartableProcessVO;
import com.paddleocr.web.model.vo.UserBriefVO;
import com.paddleocr.web.security.SecurityUtils;
import com.paddleocr.web.service.approval.FlowInstanceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 审批实例接口（业务审批域，登录即可访问；发起/撤销/可见性由 Service 层校验）
 *
 * @author paddleocr
 * @date 2026-10-12
 */
@Tag(name = "审批流程-实例运行")
@RestController
@RequestMapping("/api/approval/instance")
@RequiredArgsConstructor
public class FlowInstanceController {

    private final FlowInstanceService flowInstanceService;

    /**
     * 可发起流程列表（PUBLISHED + 发起范围过滤）
     */
    @Operation(summary = "可发起流程列表")
    @GetMapping("/startable")
    public ApiResponse<List<StartableProcessVO>> startable() {
        return ApiResponse.ok(flowInstanceService.startable(SecurityUtils.getUserId()));
    }

    /**
     * 发起流程（单据级发起锁防重复提交，返回实例 ID）
     */
    @Operation(summary = "发起流程")
    @PostMapping("/start")
    @RepeatSubmit
    public ApiResponse<String> start(@RequestBody @Validated InstanceStartDTO dto) {
        return ApiResponse.ok(flowInstanceService.start(dto, SecurityUtils.getUserId()));
    }

    /**
     * 我发起的分页（status/keyword 过滤，RUNNING 附当前待办人）
     */
    @Operation(summary = "我发起的实例分页")
    @GetMapping("/mine")
    public ApiResponse<PageResultVO<InstanceItemVO>> mine(
            @RequestParam(value = "pageNum", required = false) Integer pageNum,
            @RequestParam(value = "pageSize", required = false) Integer pageSize,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "keyword", required = false) String keyword) {
        RuntimePageQuery query = new RuntimePageQuery();
        query.setPageNum(pageNum);
        query.setPageSize(pageSize);
        query.setStatus(status);
        query.setKeyword(keyword);
        return ApiResponse.ok(flowInstanceService.pageMine(query, SecurityUtils.getUserId()));
    }

    /**
     * 实例详情（参与人可见性 40059：发起人/审批人/抄送人/ADMIN）
     */
    @Operation(summary = "实例详情")
    @GetMapping("/{id}")
    public ApiResponse<InstanceDetailVO> detail(@PathVariable("id") Long id) {
        return ApiResponse.ok(flowInstanceService.detail(id, SecurityUtils.getUserId()));
    }

    /**
     * 撤销实例（仅发起人 + RUNNING）
     */
    @Operation(summary = "撤销实例")
    @PostMapping("/{id}/cancel")
    @RepeatSubmit
    public ApiResponse<Void> cancel(@PathVariable("id") Long id,
                                    @RequestBody(required = false) InstanceCancelDTO dto) {
        flowInstanceService.cancel(id, dto, SecurityUtils.getUserId());
        return ApiResponse.ok();
    }

    /**
     * 预览下一审批节点审批人（发起前表单联动；conditions 按 §4.6.3 求值）
     */
    @Operation(summary = "预览下一审批节点审批人")
    @PostMapping("/next-approvers")
    public ApiResponse<NextApproverVO> nextApprovers(@RequestBody @Validated NextApproverQueryDTO dto) {
        return ApiResponse.ok(flowInstanceService.nextApprovers(dto, SecurityUtils.getUserId()));
    }

    /**
     * 预览节点审批动作按钮（任务未生成场景；nodeId 缺省取发起后第一个审批节点）
     */
    @Operation(summary = "预览节点审批动作按钮")
    @PostMapping("/node-buttons")
    public ApiResponse<NodeButtonsVO> nodeButtons(@RequestBody @Validated NodeButtonsQueryDTO dto) {
        return ApiResponse.ok(flowInstanceService.nodeButtons(dto, SecurityUtils.getUserId()));
    }

    /**
     * 单据审批状态批量查询（每单据取最新实例，返回 bizId → 状态）
     */
    @Operation(summary = "单据审批状态批量查询")
    @GetMapping("/biz-status")
    public ApiResponse<Map<String, BizApprovalStatusVO>> bizStatus(
            @RequestParam("bizType") String bizType,
            @RequestParam("bizIds") String bizIds) {
        List<Long> parsed = new ArrayList<>();
        for (String part : bizIds.split(",")) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            try {
                parsed.add(Long.parseLong(trimmed));
            } catch (NumberFormatException e) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "bizIds 非法");
            }
        }
        List<BizApprovalStatusVO> statuses = flowInstanceService.bizStatus(bizType, parsed);
        Map<String, BizApprovalStatusVO> result = new LinkedHashMap<>(statuses.size() * 2);
        for (BizApprovalStatusVO status : statuses) {
            result.put(status.getBizId(), status);
        }
        return ApiResponse.ok(result);
    }

    /**
     * 运行态用户选项查询（登录即可，供转办/加签/抄送选人；前缀匹配用户名/昵称，仅启用用户）
     */
    @Operation(summary = "运行态用户选项查询")
    @GetMapping("/principal-users")
    public ApiResponse<List<UserBriefVO>> principalUsers(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "limit", required = false, defaultValue = "50") Integer limit) {
        return ApiResponse.ok(flowInstanceService.principalUsers(keyword, limit == null ? 50 : limit));
    }
}
