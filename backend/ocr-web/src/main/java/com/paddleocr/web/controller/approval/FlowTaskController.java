package com.paddleocr.web.controller.approval;

import com.paddleocr.web.common.ApiResponse;
import com.paddleocr.web.common.BusinessException;
import com.paddleocr.web.common.ResultCode;
import com.paddleocr.web.model.PageResultVO;
import com.paddleocr.web.model.dto.RuntimePageQuery;
import com.paddleocr.web.model.dto.TaskActionDTO;
import com.paddleocr.web.model.vo.TaskDetailVO;
import com.paddleocr.web.model.vo.TaskVO;
import com.paddleocr.web.security.LoginUser;
import com.paddleocr.web.service.approval.FlowTaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 审批任务接口（业务审批域，登录即可访问；assignee/按钮校验在引擎层）
 *
 * @author paddleocr
 * @date 2026-10-12
 */
@Tag(name = "审批流程-任务办理")
@RestController
@RequestMapping("/api/approval/task")
@RequiredArgsConstructor
public class FlowTaskController {

    private final FlowTaskService flowTaskService;

    /**
     * 我的待办分页（keyword 命中实例标题/流程名）
     */
    @Operation(summary = "我的待办分页")
    @GetMapping("/todo")
    public ApiResponse<PageResultVO<TaskVO>> todo(
            @RequestParam(value = "pageNum", required = false) Integer pageNum,
            @RequestParam(value = "pageSize", required = false) Integer pageSize,
            @RequestParam(value = "keyword", required = false) String keyword) {
        RuntimePageQuery query = new RuntimePageQuery();
        query.setPageNum(pageNum);
        query.setPageSize(pageSize);
        query.setKeyword(keyword);
        return ApiResponse.ok(flowTaskService.pageTodo(query, currentUserId()));
    }

    /**
     * 我的已办分页（action 过滤：PASS/REJECT/RETURN/SUBMIT/TRANSFER/DELEGATE...）
     */
    @Operation(summary = "我的已办分页")
    @GetMapping("/done")
    public ApiResponse<PageResultVO<TaskVO>> done(
            @RequestParam(value = "pageNum", required = false) Integer pageNum,
            @RequestParam(value = "pageSize", required = false) Integer pageSize,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "action", required = false) String action) {
        RuntimePageQuery query = new RuntimePageQuery();
        query.setPageNum(pageNum);
        query.setPageSize(pageSize);
        query.setKeyword(keyword);
        query.setAction(action);
        return ApiResponse.ok(flowTaskService.pageDone(query, currentUserId()));
    }

    /**
     * 任务办理视图（实例详情 + 任务 + 按钮 + 可退回目标；参与人可见性 40059）
     */
    @Operation(summary = "任务办理视图")
    @GetMapping("/{taskId}")
    public ApiResponse<TaskDetailVO> detail(@PathVariable("taskId") Long taskId) {
        return ApiResponse.ok(flowTaskService.detail(taskId, currentUserId()));
    }

    /**
     * 审批动作统一入口（PASS/REJECT/RETURN/TRANSFER/DELEGATE/ADD_SIGN/COMMENT/COPY/SUBMIT）
     */
    @Operation(summary = "审批动作统一入口")
    @PostMapping("/{taskId}/action")
    public ApiResponse<Void> action(@PathVariable("taskId") Long taskId,
                                    @RequestBody @Validated TaskActionDTO dto) {
        flowTaskService.action(taskId, dto, currentUserId());
        return ApiResponse.ok();
    }

    /**
     * 从 SecurityContextHolder 取当前登录用户 ID
     */
    private Long currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof LoginUser)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        return ((LoginUser) authentication.getPrincipal()).getUserId();
    }
}
