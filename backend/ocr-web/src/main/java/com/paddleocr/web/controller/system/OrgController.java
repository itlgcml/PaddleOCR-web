package com.paddleocr.web.controller.system;

import com.paddleocr.web.common.ApiResponse;
import com.paddleocr.web.model.dto.OrgSaveDTO;
import com.paddleocr.web.model.dto.OrgUpdateDTO;
import com.paddleocr.web.model.vo.OrgTreeVO;
import com.paddleocr.web.service.system.OrgService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 机构管理接口（系统设置域，仅 ADMIN）
 *
 * @author paddleocr
 * @date 2026-09-22
 */
@Tag(name = "系统设置-机构管理")
@RestController
@RequestMapping("/api/orgs")
@RequiredArgsConstructor
public class OrgController {

    private final OrgService orgService;

    /**
     * 机构树查询（一次查全表内存组树）
     */
    @Operation(summary = "机构树")
    @GetMapping("/tree")
    public ApiResponse<List<OrgTreeVO>> tree() {
        return ApiResponse.ok(orgService.listTree());
    }

    /**
     * 新增机构
     */
    @Operation(summary = "新增机构")
    @PostMapping("/create")
    public ApiResponse<String> create(@RequestBody @Validated OrgSaveDTO dto) {
        return ApiResponse.ok(orgService.saveOrg(dto));
    }

    /**
     * 修改机构（名称/排序/状态；orgCode、parentId 禁改）
     */
    @Operation(summary = "修改机构")
    @PutMapping("/update/{id}")
    public ApiResponse<Void> update(@PathVariable("id") Long id,
                                    @RequestBody @Validated OrgUpdateDTO dto) {
        orgService.updateOrg(id, dto);
        return ApiResponse.ok();
    }

    /**
     * 删除机构（三重校验：存在 → 无子机构 → 无在职员工）
     */
    @Operation(summary = "删除机构")
    @DeleteMapping("/delete/{id}")
    public ApiResponse<Void> delete(@PathVariable("id") Long id) {
        orgService.removeOrg(id);
        return ApiResponse.ok();
    }
}
