package com.paddleocr.web.controller.approval;

import com.paddleocr.web.common.ApiResponse;
import com.paddleocr.web.model.PageResultVO;
import com.paddleocr.web.model.dto.RuntimePageQuery;
import com.paddleocr.web.model.vo.CcVO;
import com.paddleocr.web.security.SecurityUtils;
import com.paddleocr.web.service.approval.FlowCcService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 抄送接口（业务审批域，登录即可访问；仅 receiver 本人可标记已阅）
 *
 * @author paddleocr
 * @date 2026-10-12
 */
@Tag(name = "审批流程-抄送待阅")
@RestController
@RequestMapping("/api/approval/cc")
@RequiredArgsConstructor
public class FlowCcController {

    private final FlowCcService flowCcService;

    /**
     * 我的待阅分页（keyword 命中实例标题/流程名）
     */
    @Operation(summary = "我的待阅分页")
    @GetMapping("/unread")
    public ApiResponse<PageResultVO<CcVO>> unread(
            @RequestParam(value = "pageNum", required = false) Integer pageNum,
            @RequestParam(value = "pageSize", required = false) Integer pageSize,
            @RequestParam(value = "keyword", required = false) String keyword) {
        return ApiResponse.ok(page(pageNum, pageSize, keyword, false));
    }

    /**
     * 我的已阅分页
     */
    @Operation(summary = "我的已阅分页")
    @GetMapping("/read")
    public ApiResponse<PageResultVO<CcVO>> read(
            @RequestParam(value = "pageNum", required = false) Integer pageNum,
            @RequestParam(value = "pageSize", required = false) Integer pageSize,
            @RequestParam(value = "keyword", required = false) String keyword) {
        return ApiResponse.ok(page(pageNum, pageSize, keyword, true));
    }

    /**
     * 标记已阅（幂等）
     */
    @Operation(summary = "标记已阅")
    @PostMapping("/{ccId}/read")
    public ApiResponse<Void> markRead(@PathVariable("ccId") Long ccId) {
        flowCcService.markRead(ccId, SecurityUtils.getUserId());
        return ApiResponse.ok();
    }

    private PageResultVO<CcVO> page(Integer pageNum, Integer pageSize, String keyword, boolean read) {
        RuntimePageQuery query = new RuntimePageQuery();
        query.setPageNum(pageNum);
        query.setPageSize(pageSize);
        query.setKeyword(keyword);
        return flowCcService.pageMine(query, SecurityUtils.getUserId(), read);
    }
}
