package com.paddleocr.web.controller.system;

import com.paddleocr.web.common.ApiResponse;
import com.paddleocr.web.common.BusinessException;
import com.paddleocr.web.common.ResultCode;
import com.paddleocr.web.model.PageResultVO;
import com.paddleocr.web.model.dto.UserOrgAssignDTO;
import com.paddleocr.web.model.dto.UserPageQuery;
import com.paddleocr.web.model.dto.UserRoleAssignDTO;
import com.paddleocr.web.model.dto.UserStatusDTO;
import com.paddleocr.web.model.vo.UserPageVO;
import com.paddleocr.web.security.LoginUser;
import com.paddleocr.web.service.system.UserAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户管理接口（系统设置域，仅 ADMIN；禁止对当前登录用户自己执行角色替换/禁用）
 *
 * @author paddleocr
 * @date 2026-09-22
 */
@Tag(name = "系统设置-用户管理")
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserAdminController {

    private final UserAdminService userAdminService;

    /**
     * 用户分页查询
     */
    @Operation(summary = "用户分页")
    @GetMapping("/page")
    public ApiResponse<PageResultVO<UserPageVO>> page(
            @RequestParam(value = "pageNum", required = false) Integer pageNum,
            @RequestParam(value = "pageSize", required = false) Integer pageSize,
            @RequestParam(value = "username", required = false) String username,
            @RequestParam(value = "orgId", required = false) Long orgId,
            @RequestParam(value = "status", required = false) Integer status) {
        UserPageQuery query = new UserPageQuery();
        query.setPageNum(pageNum);
        query.setPageSize(pageSize);
        query.setUsername(username);
        query.setOrgId(orgId);
        query.setStatus(status);
        return ApiResponse.ok(userAdminService.pageUsers(query));
    }

    /**
     * 调整用户所属机构
     */
    @Operation(summary = "调整所属机构")
    @PutMapping("/org/{id}")
    public ApiResponse<Void> assignOrg(@PathVariable("id") Long id,
                                       @RequestBody @Validated UserOrgAssignDTO dto) {
        userAdminService.updateUserOrg(id, dto.getOrgId());
        return ApiResponse.ok();
    }

    /**
     * 分配角色（全量替换；不允许对当前登录用户自己执行，防管理员自锁）
     */
    @Operation(summary = "分配角色（全量替换）")
    @PutMapping("/roles/{id}")
    public ApiResponse<Void> assignRoles(@PathVariable("id") Long id,
                                         @RequestBody @Validated UserRoleAssignDTO dto) {
        rejectSelfOperation(id);
        userAdminService.updateUserRoles(id, dto.getRoleIds());
        return ApiResponse.ok();
    }

    /**
     * 启用/禁用用户（禁用即时生效；不允许禁用当前登录用户自己）
     */
    @Operation(summary = "启用/禁用")
    @PutMapping("/status/{id}")
    public ApiResponse<Void> updateStatus(@PathVariable("id") Long id,
                                          @RequestBody @Validated UserStatusDTO dto) {
        rejectSelfOperation(id);
        userAdminService.updateUserStatus(id, dto.getStatus());
        return ApiResponse.ok();
    }

    /**
     * 拒绝对当前登录用户自己的操作（防管理员自锁）
     */
    private void rejectSelfOperation(Long targetUserId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof LoginUser) {
            Long currentUserId = ((LoginUser) authentication.getPrincipal()).getUserId();
            if (currentUserId.equals(targetUserId)) {
                throw new BusinessException(ResultCode.FORBIDDEN);
            }
        }
    }
}
