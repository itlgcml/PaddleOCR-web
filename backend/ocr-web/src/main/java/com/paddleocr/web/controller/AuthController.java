package com.paddleocr.web.controller;

import com.paddleocr.web.common.ApiResponse;
import com.paddleocr.web.common.BusinessException;
import com.paddleocr.web.common.ResultCode;
import com.paddleocr.web.mapper.entity.UserDO;
import com.paddleocr.web.model.dto.LoginDTO;
import com.paddleocr.web.model.dto.LogoutDTO;
import com.paddleocr.web.model.dto.RefreshTokenDTO;
import com.paddleocr.web.model.dto.RegisterDTO;
import com.paddleocr.web.model.vo.LoginVO;
import com.paddleocr.web.model.vo.TokenVO;
import com.paddleocr.web.model.vo.UserInfoVO;
import com.paddleocr.web.security.LoginUser;
import com.paddleocr.web.service.AuthService;
import com.paddleocr.web.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;

/**
 * 认证域接口：注册/登录/刷新令牌/当前用户/登出
 *
 * @author paddleocr
 * @date 2026-09-22
 */
@Tag(name = "认证")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final String AUTHORIZATION_HEADER = "Authorization";

    private static final String BEARER_PREFIX = "Bearer ";

    private static final String FORWARDED_FOR_HEADER = "X-Forwarded-For";

    private final UserService userService;
    private final AuthService authService;
    private final AuthenticationManager authenticationManager;

    /**
     * 注册（匿名）：默认挂 DEFAULT 机构、自动绑定 USER 角色
     */
    @Operation(summary = "注册")
    @PostMapping("/register")
    public ApiResponse<UserInfoVO> register(@RequestBody @Validated RegisterDTO dto) {
        return ApiResponse.ok(userService.register(dto));
    }

    /**
     * 登录（匿名）：认证走 AuthenticationManager，编排层只做异常翻译与失败计数
     */
    @Operation(summary = "登录")
    @PostMapping("/login")
    public ApiResponse<LoginVO> login(@RequestBody @Validated LoginDTO dto, HttpServletRequest request) {
        LoginUser principal;
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(dto.getUsername(), dto.getPassword()));
            principal = (LoginUser) authentication.getPrincipal();
        } catch (BadCredentialsException | UsernameNotFoundException e) {
            // 用户不存在被 DaoAuthenticationProvider 掩码为 BadCredentials → 统一 40022（防账号枚举）
            recordFailureByUsername(dto.getUsername());
            throw new BusinessException(ResultCode.PASSWORD_ERROR);
        } catch (DisabledException e) {
            throw new BusinessException(ResultCode.USER_DISABLED);
        } catch (LockedException e) {
            throw new BusinessException(ResultCode.ACCOUNT_LOCKED);
        }
        return ApiResponse.ok(authService.login(principal.getUserId(), resolveClientIp(request)));
    }

    /**
     * 刷新令牌（匿名）：accessToken 过期后静默续期
     */
    @Operation(summary = "刷新令牌")
    @PostMapping("/refresh")
    public ApiResponse<TokenVO> refresh(@RequestBody @Validated RefreshTokenDTO dto) {
        return ApiResponse.ok(authService.refresh(dto.getRefreshToken()));
    }

    /**
     * 当前用户信息（鉴权）：实时查库，角色/机构/禁用状态即时生效
     */
    @Operation(summary = "当前用户信息")
    @GetMapping("/me")
    public ApiResponse<UserInfoVO> me() {
        LoginUser loginUser = currentLoginUser();
        return ApiResponse.ok(userService.getUserInfo(loginUser.getUserId()));
    }

    /**
     * 登出（鉴权）：把双令牌 jti 写入 Redis 黑名单，旧 Token 立即失效
     */
    @Operation(summary = "登出")
    @PostMapping("/logout")
    public ApiResponse<Void> logout(@RequestBody(required = false) LogoutDTO dto,
                                    HttpServletRequest request) {
        authService.logout(extractBearerToken(request), dto);
        return ApiResponse.ok();
    }

    /**
     * 认证失败后按用户名记录失败计数（用户不存在时跳过）
     */
    private void recordFailureByUsername(String username) {
        UserDO user = userService.getByUsername(username);
        if (user != null) {
            userService.recordLoginFailure(user.getId());
        }
    }

    /**
     * 从 SecurityContextHolder 取当前登录用户
     */
    private LoginUser currentLoginUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof LoginUser)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        return (LoginUser) authentication.getPrincipal();
    }

    /**
     * 从 Header 提取 Bearer Token
     */
    private String extractBearerToken(HttpServletRequest request) {
        String header = request.getHeader(AUTHORIZATION_HEADER);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return null;
        }
        return header.substring(BEARER_PREFIX.length());
    }

    /**
     * 解析客户端 IP（优先取 X-Forwarded-For 首段）
     */
    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader(FORWARDED_FOR_HEADER);
        if (forwarded != null && !forwarded.isEmpty()) {
            int commaIndex = forwarded.indexOf(',');
            return commaIndex > 0 ? forwarded.substring(0, commaIndex).trim() : forwarded.trim();
        }
        return request.getRemoteAddr();
    }
}
