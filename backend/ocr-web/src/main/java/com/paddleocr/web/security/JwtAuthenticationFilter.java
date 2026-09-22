package com.paddleocr.web.security;

import com.paddleocr.web.common.BusinessException;
import com.paddleocr.web.common.ResultCode;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * JWT 请求鉴权过滤器（挂入 SecurityFilterChain，位于 UsernamePasswordAuthenticationFilter 之前）
 *
 * <p>核心约束：本 Filter 位于 ExceptionTranslationFilter 之前，Filter 内抛出的
 * AuthenticationException 传播不到 RestAuthEntryPoint（会穿透成 500），所有 Token
 * 异常必须自行 try-catch 并手动调用 restAuthEntryPoint.commence() 写响应后 return</p>
 *
 * @author paddleocr
 * @date 2026-09-22
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;
    private final AuthProperties authProperties;
    private final TokenBlacklistService tokenBlacklistService;
    private final UserDetailsServiceImpl userDetailsService;
    private final RestAuthEntryPoint restAuthEntryPoint;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader(AUTHORIZATION_HEADER);
        // 无 Authorization 头或非 Bearer 格式：不写响应、不放认证，由链尾触发 RestAuthEntryPoint（40100）
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }
        try {
            String token = header.substring(BEARER_PREFIX.length());
            // 过期抛 40102 / 签名不符或篡改抛 40101
            Claims claims = jwtTokenProvider.parse(token);
            // 只认 type=access：refreshToken 直接当 accessToken 用 → 40101（堵死 7d 长效 Token 绕过短有效期）
            if (!JwtTokenProvider.TYPE_ACCESS.equals(claims.get(JwtTokenProvider.CLAIM_TYPE, String.class))) {
                throw new AuthFailureException(ResultCode.TOKEN_INVALID);
            }
            // 黑名单校验：降级开关关闭时一并跳过；Redis 读故障 fail-open 放行
            if (Boolean.TRUE.equals(authProperties.getEnabled())
                    && tokenBlacklistService.isRevoked(claims.getId())) {
                throw new AuthFailureException(ResultCode.TOKEN_REVOKED);
            }
            // 装载用户 + 角色（每次查库，禁用/撤权即时生效）
            UserDetails userDetails = userDetailsService
                    .loadUserByUsername(claims.get(JwtTokenProvider.CLAIM_USERNAME, String.class));
            if (!userDetails.isEnabled()) {
                throw new AuthFailureException(ResultCode.USER_DISABLED);
            }
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (AuthFailureException e) {
            // Filter 内手动接管输出，异常传播不到 ExceptionTranslationFilter
            restAuthEntryPoint.commence(request, response, e);
            return;
        } catch (BusinessException e) {
            // JwtTokenProvider.parse 抛出的 TOKEN_EXPIRED / TOKEN_INVALID
            restAuthEntryPoint.commence(request, response, new AuthFailureException(mapResultCode(e)));
            return;
        } catch (AuthenticationException e) {
            // 用户装载失败（如 Token 签发后用户被逻辑删除）：统一 40100 手动接管
            restAuthEntryPoint.commence(request, response,
                    new AuthFailureException(ResultCode.UNAUTHORIZED));
            return;
        }
        filterChain.doFilter(request, response);
    }

    /**
     * BusinessException 的 code 映射回 ResultCode（仅 40102/40101 两种取值）
     */
    private ResultCode mapResultCode(BusinessException e) {
        return e.getCode() == ResultCode.TOKEN_EXPIRED.getCode()
                ? ResultCode.TOKEN_EXPIRED : ResultCode.TOKEN_INVALID;
    }
}
