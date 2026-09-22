package com.paddleocr.web.security;

import com.paddleocr.web.common.BusinessException;
import com.paddleocr.web.common.ResultCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

/**
 * JWT 令牌签发与解析（HS256）
 *
 * <p>Claims：sub=userId(String)、username、type(access/refresh)、jti=UUID、iat、exp；
 * 禁止把密码/邮箱放 payload（JWT 仅 Base64Url 编码，非加密）；角色不放 JWT（撤权即时生效）</p>
 *
 * @author paddleocr
 * @date 2026-09-22
 */
@Slf4j
@Component
public class JwtTokenProvider {

    /** Token 类型：访问令牌 */
    public static final String TYPE_ACCESS = "access";

    /** Token 类型：刷新令牌 */
    public static final String TYPE_REFRESH = "refresh";

    /** Claims 键：用户名（Filter/refresh 装载用户时读取） */
    public static final String CLAIM_USERNAME = "username";

    /** Claims 键：令牌类型（access/refresh，防 refresh 冒充 access） */
    public static final String CLAIM_TYPE = "type";

    /** HS256 密钥最小字节数（防弱密钥启动后签发失败） */
    private static final int MIN_SECRET_BYTES = 32;

    private final AuthProperties authProperties;

    private final SecretKey signingKey;

    public JwtTokenProvider(AuthProperties authProperties) {
        this.authProperties = authProperties;
        byte[] secretBytes = authProperties.getSecret() == null
                ? new byte[0] : authProperties.getSecret().getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("security.jwt.secret 长度不足 32 字符，HS256 要求至少 32 字符");
        }
        this.signingKey = Keys.hmacShaKeyFor(secretBytes);
    }

    /**
     * 签发访问令牌（默认 2 小时）
     *
     * @param userId 用户 ID
     * @param username 用户名
     * @return JWT 字符串
     */
    public String generateAccessToken(Long userId, String username) {
        return buildToken(String.valueOf(userId), username, TYPE_ACCESS,
                authProperties.getAccessTokenTtl());
    }

    /**
     * 签发刷新令牌（默认 7 天）
     *
     * @param userId 用户 ID
     * @return JWT 字符串
     */
    public String generateRefreshToken(Long userId) {
        return buildToken(String.valueOf(userId), null, TYPE_REFRESH,
                authProperties.getRefreshTokenTtl());
    }

    /**
     * 解析并验签 Token
     *
     * <p>过期抛 TOKEN_EXPIRED、签名不符/被篡改抛 TOKEN_INVALID——不做内部消化，
     * 输出通路由调用方决定（Filter 包成 AuthFailureException 经 EntryPoint 写出；refresh 接口转 40025）</p>
     *
     * @param token JWT 字符串
     * @return Claims
     */
    public Claims parse(String token) {
        try {
            return Jwts.parserBuilder()
                    .setSigningKey(signingKey)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
        } catch (ExpiredJwtException e) {
            throw new BusinessException(ResultCode.TOKEN_EXPIRED);
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("JWT 解析失败: {}", e.getMessage());
            throw new BusinessException(ResultCode.TOKEN_INVALID);
        }
    }

    /**
     * Token 剩余有效秒数（黑名单 TTL 依据：Token 自然过期后黑名单条目同步消失）
     *
     * @param claims 已解析的 Claims
     * @return 剩余秒数，已过期返回 0
     */
    public long getRemainingSeconds(Claims claims) {
        long remainingMillis = claims.getExpiration().getTime() - System.currentTimeMillis();
        return Math.max(remainingMillis / 1000L, 0L);
    }

    /**
     * accessToken 有效秒数（登录/刷新出参 expiresIn）
     *
     * @return 配置值（默认 7200）
     */
    public int getAccessTokenTtlSeconds() {
        return authProperties.getAccessTokenTtl();
    }

    /**
     * 构建统一 Claims 的 JWT
     */
    private String buildToken(String subject, String username, String type, int ttlSeconds) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + ttlSeconds * 1000L);
        return Jwts.builder()
                .setSubject(subject)
                .claim(CLAIM_USERNAME, username)
                .claim(CLAIM_TYPE, type)
                .setId(UUID.randomUUID().toString())
                .setIssuedAt(now)
                .setExpiration(expiration)
                .signWith(signingKey, SignatureAlgorithm.HS256)
                .compact();
    }
}
