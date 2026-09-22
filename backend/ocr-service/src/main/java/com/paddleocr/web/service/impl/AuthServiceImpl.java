package com.paddleocr.web.service.impl;

import cn.hutool.core.util.StrUtil;
import com.paddleocr.web.common.BusinessException;
import com.paddleocr.web.common.ResultCode;
import com.paddleocr.web.mapper.UserMapper;
import com.paddleocr.web.mapper.entity.UserDO;
import com.paddleocr.web.model.dto.LogoutDTO;
import com.paddleocr.web.model.vo.LoginVO;
import com.paddleocr.web.model.vo.TokenVO;
import com.paddleocr.web.model.vo.UserInfoVO;
import com.paddleocr.web.security.JwtTokenProvider;
import com.paddleocr.web.security.TokenBlacklistService;
import com.paddleocr.web.service.AuthService;
import com.paddleocr.web.service.UserService;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 认证服务实现：令牌签发、刷新与登出吊销编排
 *
 * @author paddleocr
 * @date 2026-09-22
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    /** 令牌类型出参固定值 */
    private static final String TOKEN_TYPE_BEARER = "Bearer";

    private final UserMapper userMapper;
    private final UserService userService;
    private final JwtTokenProvider jwtTokenProvider;
    private final TokenBlacklistService tokenBlacklistService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LoginVO login(Long userId, String loginIp) {
        UserDO user = userMapper.selectById(userId);
        if (user == null) {
            // 认证已通过，正常流程不会走到这里
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        LoginVO vo = new LoginVO();
        vo.setAccessToken(jwtTokenProvider.generateAccessToken(userId, user.getUsername()));
        vo.setRefreshToken(jwtTokenProvider.generateRefreshToken(userId));
        vo.setTokenType(TOKEN_TYPE_BEARER);
        vo.setExpiresIn(jwtTokenProvider.getAccessTokenTtlSeconds());
        UserInfoVO userInfo = userService.getUserInfo(userId);
        vo.setUser(userInfo);
        // 登录成功短事务：清零失败计数 + 写入最近登录时间/IP
        userService.recordLoginSuccess(userId, loginIp);
        log.info("用户登录成功, userId={}, username={}", userId, user.getUsername());
        return vo;
    }

    @Override
    public TokenVO refresh(String refreshToken) {
        Claims claims = parseToken(refreshToken);
        // 只认 type=refresh：accessToken 传入 → 40025
        if (!JwtTokenProvider.TYPE_REFRESH.equals(claims.get(JwtTokenProvider.CLAIM_TYPE, String.class))) {
            throw new BusinessException(ResultCode.REFRESH_TOKEN_INVALID);
        }
        // 已登出吊销的 refreshToken → 40025（堵死"登出后复活"路径）
        if (tokenBlacklistService.isRevoked(claims.getId())) {
            throw new BusinessException(ResultCode.REFRESH_TOKEN_INVALID);
        }
        Long userId = Long.valueOf(claims.getSubject());
        UserDO user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.REFRESH_TOKEN_INVALID);
        }
        TokenVO vo = new TokenVO();
        vo.setAccessToken(jwtTokenProvider.generateAccessToken(userId, user.getUsername()));
        vo.setTokenType(TOKEN_TYPE_BEARER);
        vo.setExpiresIn(jwtTokenProvider.getAccessTokenTtlSeconds());
        return vo;
    }

    @Override
    public void logout(String accessToken, LogoutDTO logoutDTO) {
        if (StrUtil.isBlank(accessToken)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        Claims accessClaims = jwtTokenProvider.parse(accessToken);
        tokenBlacklistService.revoke(accessClaims.getId(),
                jwtTokenProvider.getRemainingSeconds(accessClaims));
        String refreshToken = logoutDTO == null ? null : logoutDTO.getRefreshToken();
        if (StrUtil.isNotBlank(refreshToken)) {
            // refreshToken 无效/过期不阻断登出（已过期无需吊销），仅记录
            try {
                Claims refreshClaims = jwtTokenProvider.parse(refreshToken);
                tokenBlacklistService.revoke(refreshClaims.getId(),
                        jwtTokenProvider.getRemainingSeconds(refreshClaims));
            } catch (BusinessException e) {
                log.warn("登出时 refreshToken 吊销跳过: {}", e.getMessage());
            }
        }
        log.info("用户登出, sub={}", accessClaims.getSubject());
    }

    /**
     * 解析刷新令牌（过期/篡改统一转 40025）
     */
    private Claims parseToken(String refreshToken) {
        try {
            return jwtTokenProvider.parse(refreshToken);
        } catch (BusinessException e) {
            throw new BusinessException(ResultCode.REFRESH_TOKEN_INVALID);
        }
    }
}
