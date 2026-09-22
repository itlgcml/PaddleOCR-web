package com.paddleocr.web.security;

import com.paddleocr.web.common.BusinessException;
import com.paddleocr.web.common.ResultCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Token 吊销黑名单（Redis KV，仅存在性语义，不承载会话）
 *
 * <p>Key：auth:blacklist:{jti}，value 固定 1，TTL = Token 剩余有效期（到期自动清理零积压）；
 * 读故障 fail-open 放行（可用性优先，ERROR 记录）；写故障抛 SYSTEM_ERROR 不静默成功</p>
 *
 * @author paddleocr
 * @date 2026-09-22
 */
@Slf4j
@Component
public class TokenBlacklistService {

    /** Key 前缀：auth:blacklist:{jti} */
    private static final String KEY_PREFIX = "auth:blacklist:";

    /** 固定 value（只需存在性语义） */
    private static final String VALUE = "1";

    private final StringRedisTemplate redisTemplate;

    public TokenBlacklistService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 吊销指定 Token（TTL = 剩余有效期，到期自动清理）
     *
     * @param jti Token 唯一标识
     * @param ttlSeconds 剩余有效秒数
     */
    public void revoke(String jti, long ttlSeconds) {
        if (ttlSeconds <= 0L) {
            // 已过期的 Token 无需吊销，直接返回
            return;
        }
        try {
            redisTemplate.opsForValue().set(KEY_PREFIX + jti, VALUE, ttlSeconds, TimeUnit.SECONDS);
        } catch (Exception e) {
            // 吊销失败不可静默成功（安全语义：登出提示成功但旧 Token 仍有效是漏洞）
            log.error("Redis 黑名单写入失败, jti={}", jti, e);
            throw new BusinessException(ResultCode.SYSTEM_ERROR);
        }
    }

    /**
     * 校验 Token 是否已被吊销
     *
     * <p>Redis 读故障一律返回 false（fail-open 放行），由 accessToken 短有效期兜底</p>
     *
     * @param jti Token 唯一标识
     * @return true=已吊销
     */
    public boolean isRevoked(String jti) {
        try {
            Boolean exists = redisTemplate.hasKey(KEY_PREFIX + jti);
            return Boolean.TRUE.equals(exists);
        } catch (Exception e) {
            log.error("Redis 连接失败，黑名单读取降级放行（fail-open）, jti={}", jti, e);
            return false;
        }
    }
}
