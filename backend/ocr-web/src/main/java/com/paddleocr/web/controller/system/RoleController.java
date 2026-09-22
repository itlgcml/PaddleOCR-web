package com.paddleocr.web.controller.system;

import com.paddleocr.web.common.ApiResponse;
import com.paddleocr.web.model.PageResultVO;
import com.paddleocr.web.model.dto.RoleSaveDTO;
import com.paddleocr.web.model.dto.RoleUpdateDTO;
import com.paddleocr.web.model.vo.RoleVO;
import com.paddleocr.web.service.system.RoleService;
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

import java.util.List;

/**
 * 角色管理接口（系统设置域，仅 ADMIN）
 *
 * @author paddleocr
 * @date 2026-09-22
 */
@Tag(name = "系统设置-角色管理")
@RestController
@RequestMapping("/api/roles")
@RequiredArgsConstructor
public class RoleController {

    private final RoleService roleService;

    /**
     * 角色分页查询
     */
    @Operation(summary = "角色分页")
    @GetMapping("/page")
    public ApiResponse<PageResultVO<RoleVO>> page(
            @RequestParam(value = "pageNum", required = false, defaultValue = "1") Integer pageNum,
            @RequestParam(value = "pageSize", required = false, defaultValue = "10") Integer pageSize,
            @RequestParam(value = "keyword", required = false) String keyword) {
        return ApiResponse.ok(roleService.pageRoles(pageNum, pageSize, keyword));
    }

    /**
     * 全量启用角色（供用户分配角色下拉框）
     */
    @Operation(summary = "全量启用角色")
    @GetMapping("/all")
    public ApiResponse<List<RoleVO>> listAll() {
        return ApiResponse.ok(roleService.listEnabledRoles());
    }

    /**
     * 新增角色
     */
    @Operation(summary = "新增角色")
    @PostMapping("/create")
    public ApiResponse<String> create(@RequestBody @Validated RoleSaveDTO dto) {
        return ApiResponse.ok(roleService.saveRole(dto));
    }

    /**
     * 修改角色（内置角色禁止修改）
     */
    @Operation(summary = "修改角色")
    @PutMapping("/update/{id}")
    public ApiResponse<Void> update(@PathVariable("id") Long id,
                                    @RequestBody @Validated RoleUpdateDTO dto) {
        roleService.updateRole(id, dto);
        return ApiResponse.ok();
    }

    /**
     * 删除角色（内置角色保护 + 绑定校验）
     */
    @Operation(summary = "删除角色")
    @DeleteMapping("/delete/{id}")
    public ApiResponse<Void> delete(@PathVariable("id") Long id) {
        roleService.removeRole(id);
        return ApiResponse.ok();
    }
}
