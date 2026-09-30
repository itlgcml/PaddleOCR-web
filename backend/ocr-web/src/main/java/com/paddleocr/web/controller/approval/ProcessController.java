package com.paddleocr.web.controller.approval;

import com.paddleocr.web.common.ApiResponse;
import com.paddleocr.web.model.PageResultVO;
import com.paddleocr.web.model.dto.ProcessDesignSaveDTO;
import com.paddleocr.web.model.dto.ProcessPageQuery;
import com.paddleocr.web.model.dto.ProcessSaveDTO;
import com.paddleocr.web.model.dto.ProcessUpdateDTO;
import com.paddleocr.web.model.vo.ProcessDesignVO;
import com.paddleocr.web.model.vo.ProcessDetailVO;
import com.paddleocr.web.model.vo.ProcessVersionVO;
import com.paddleocr.web.model.vo.ProcessVO;
import com.paddleocr.web.service.approval.ProcessService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 流程配置接口（审批域，仅 ADMIN）
 *
 * @author paddleocr
 * @date 2026-09-30
 */
@Tag(name = "审批流程-流程配置")
@RestController
@RequestMapping("/api/approval/process")
@RequiredArgsConstructor
public class ProcessController {

    private final ProcessService processService;

    /**
     * 分页查询（keyword 按 code/name 前缀匹配，status 精确过滤）
     */
    @Operation(summary = "流程配置分页查询")
    @GetMapping("/page")
    public ApiResponse<PageResultVO<ProcessVO>> page(
            @RequestParam(value = "pageNum", required = false) Integer pageNum,
            @RequestParam(value = "pageSize", required = false) Integer pageSize,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "status", required = false) String status) {
        ProcessPageQuery query = new ProcessPageQuery();
        query.setPageNum(pageNum);
        query.setPageSize(pageSize);
        query.setKeyword(keyword);
        query.setStatus(status);
        return ApiResponse.ok(processService.page(query));
    }

    /**
     * 查询流程详情（基本信息 + 发起范围数组 + 当前版本号）
     */
    @Operation(summary = "流程详情")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/{id}")
    public ApiResponse<ProcessDetailVO> detail(@PathVariable("id") Long id) {
        return ApiResponse.ok(processService.detail(id));
    }

    /**
     * 新增流程（初始 DRAFT，返回新流程 ID）
     */
    @Operation(summary = "新增流程")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/create")
    public ApiResponse<String> create(@RequestBody @Validated ProcessSaveDTO dto) {
        return ApiResponse.ok(processService.create(dto));
    }

    /**
     * 更新基本信息（仅 DRAFT 态；code 建后不可改）
     */
    @Operation(summary = "更新流程基本信息")
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/update/{id}")
    public ApiResponse<Void> update(@PathVariable("id") Long id,
                                    @RequestBody @Validated ProcessUpdateDTO dto) {
        processService.update(id, dto);
        return ApiResponse.ok();
    }

    /**
     * 保存流程设计草稿（任意状态可存，仅覆盖 draft_json，防重复提交）
     */
    @Operation(summary = "保存流程设计草稿")
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}/design")
    public ApiResponse<Void> saveDesign(@PathVariable("id") Long id,
                                        @RequestBody @Validated ProcessDesignSaveDTO dto) {
        processService.saveDesign(id, dto);
        return ApiResponse.ok();
    }

    /**
     * 查询流程设计（缺省返回草稿，可指定已发布版本）
     */
    @Operation(summary = "查询流程设计")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/{id}/design")
    public ApiResponse<ProcessDesignVO> getDesign(@PathVariable("id") Long id,
                                                  @RequestParam(value = "version", required = false) String version) {
        return ApiResponse.ok(processService.getDesign(id, version));
    }

    /**
     * 发布流程（发布级校验 → 冻结新版本快照 → 状态置 PUBLISHED，防重复提交）
     */
    @Operation(summary = "发布流程")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{id}/publish")
    public ApiResponse<String> publish(@PathVariable("id") Long id) {
        return ApiResponse.ok(processService.publish(id));
    }

    /**
     * 停用流程（仅 PUBLISHED 态）
     */
    @Operation(summary = "停用流程")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{id}/disable")
    public ApiResponse<Void> disable(@PathVariable("id") Long id) {
        processService.disable(id);
        return ApiResponse.ok();
    }

    /**
     * 查询版本列表（按发布时间倒序）
     */
    @Operation(summary = "流程版本列表")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/{id}/versions")
    public ApiResponse<List<ProcessVersionVO>> versions(@PathVariable("id") Long id) {
        return ApiResponse.ok(processService.versions(id));
    }

    /**
     * 逻辑删除流程（仅 DRAFT 态，连带删除版本记录）
     */
    @Operation(summary = "删除流程")
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> remove(@PathVariable("id") Long id) {
        processService.remove(id);
        return ApiResponse.ok();
    }
}
