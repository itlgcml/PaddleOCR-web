package com.paddleocr.web.controller.lawfirmselect;

import com.paddleocr.web.common.ApiResponse;
import com.paddleocr.web.model.PageResultVO;
import com.paddleocr.web.model.dto.LawFirmSelectSaveDTO;
import com.paddleocr.web.model.vo.LawFirmSelectVO;
import com.paddleocr.web.service.lawfirmselect.LawFirmSelectService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 律所选聘管理接口（业务审批域，登录即可访问）
 *
 * @author paddleocr
 * @date 2026-09-30
 */
@Tag(name = "业务审批-律所选聘")
@RestController
@RequestMapping("/api/law-firm-select")
@RequiredArgsConstructor
public class LawFirmSelectController {

    private final LawFirmSelectService lawFirmSelectService;

    /**
     * 律所选聘分页查询
     */
    @Operation(summary = "律所选聘分页")
    @GetMapping("/page")
    public ApiResponse<PageResultVO<LawFirmSelectVO>> page(
            @RequestParam(value = "pageNum", required = false, defaultValue = "1") Integer pageNum,
            @RequestParam(value = "pageSize", required = false, defaultValue = "10") Integer pageSize,
            @RequestParam(value = "title", required = false) String title) {
        return ApiResponse.ok(lawFirmSelectService.pageLawFirmSelects(pageNum, pageSize, title));
    }

    /**
     * 律所选聘详情查询（记录不存在返回 40050）
     */
    @Operation(summary = "律所选聘详情")
    @GetMapping("/detail/{id}")
    public ApiResponse<LawFirmSelectVO> detail(@PathVariable("id") Long id) {
        return ApiResponse.ok(lawFirmSelectService.getLawFirmSelectDetail(id));
    }

    /**
     * 新增律所选聘
     */
    @Operation(summary = "新增律所选聘")
    @PostMapping("/create")
    public ApiResponse<String> create(@RequestBody @Validated LawFirmSelectSaveDTO dto) {
        return ApiResponse.ok(lawFirmSelectService.saveLawFirmSelect(dto));
    }

    /**
     * 修改律所选聘（全字段可改）
     */
    @Operation(summary = "修改律所选聘")
    @PutMapping("/update/{id}")
    public ApiResponse<Void> update(@PathVariable("id") Long id,
                                    @RequestBody @Validated LawFirmSelectSaveDTO dto) {
        lawFirmSelectService.updateLawFirmSelect(id, dto);
        return ApiResponse.ok();
    }

    /**
     * 删除律所选聘（逻辑删除）
     */
    @Operation(summary = "删除律所选聘")
    @DeleteMapping("/delete/{id}")
    public ApiResponse<Void> delete(@PathVariable("id") Long id) {
        lawFirmSelectService.removeLawFirmSelect(id);
        return ApiResponse.ok();
    }
}
