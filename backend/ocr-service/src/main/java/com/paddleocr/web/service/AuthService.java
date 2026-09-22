package com.paddleocr.web.service;

import com.paddleocr.web.model.dto.LogoutDTO;
import com.paddleocr.web.model.vo.LoginVO;
import com.paddleocr.web.model.vo.TokenVO;

/**
 * 认证服务：登录后的令牌签发、刷新与登出吊销编排
 *
 * <p>Spring Security 的 AuthenticationManager 认证编排位于 ocr-web 的 AuthController
 * （跨模块红线：security web/config 类不得出现在 ocr-service）</p>
 *
 * @author paddleocr
 * @date 2026-09-22
 */
public interface AuthService {

    /**
     * 认证通过后的登录出参组装：签发双令牌 + 用户信息 + 登录成功记录
     *
     * @param userId 用户 ID（来自 Authentication principal）
     * @param loginIp 登录 IP（由 Controller 从 HttpServletRequest 取出）
     * @return 登录 VO
     */
    LoginVO login(Long userId, String loginIp);

    /**
     * 刷新访问令牌（只认 type=refresh；已吊销/过期/类型不符均返回 40025）
     *
     * @param refreshToken 刷新令牌
     * @return 新访问令牌 VO
     */
    TokenVO refresh(String refreshToken);

    /**
     * 登出：把 accessToken（必传）与 refreshToken（可选）的 jti 写入 Redis 黑名单，旧 Token 立即失效
     *
     * @param accessToken 访问令牌
     * @param logoutDTO 登出请求（refreshToken 可选），可为 null
     */
    void logout(String accessToken, LogoutDTO logoutDTO);
}
