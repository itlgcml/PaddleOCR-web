package com.paddleocr.web.security;

import com.paddleocr.web.common.BusinessException;
import com.paddleocr.web.common.ResultCode;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 安全上下文工具类：统一从 SecurityContextHolder 获取当前登录用户，替代各 Controller 内的重复实现
 *
 * @author paddleocr
 * @date 2026-10-19
 */
public final class SecurityUtils {

    /**
     * 工具类禁止实例化
     */
    private SecurityUtils() {
    }

    /**
     * 获取当前登录用户（未认证或匿名访问时抛出 UNAUTHORIZED）
     *
     * @return 当前登录用户主体
     */
    public static LoginUser getLoginUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof LoginUser)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        return (LoginUser) authentication.getPrincipal();
    }

    /**
     * 获取当前登录用户 ID（未认证或匿名访问时抛出 UNAUTHORIZED）
     *
     * @return 当前登录用户 ID
     */
    public static Long getUserId() {
        return getLoginUser().getUserId();
    }

    /**
     * 宽容获取当前登录用户 ID：未认证或匿名访问时返回 null，不抛异常
     *
     * @return 当前登录用户 ID；未认证时返回 null
     */
    public static Long tryGetUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof LoginUser)) {
            return null;
        }
        return ((LoginUser) authentication.getPrincipal()).getUserId();
    }
}
