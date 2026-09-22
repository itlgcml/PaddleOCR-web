package com.paddleocr.web.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 启动期 Redis 连通性探测
 *
 * <p>Lettuce 懒连接导致 Redis 故障默认在首次命令时才暴露，
 * 本探测在启动完成后立即执行一次读命令，失败打 ERROR 告警、成功打 INFO 确认；
 * 遵循 fail-open 语义：探测失败不阻断启动（command timeout 1s，最多拖慢启动 1 秒）</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisStartupChecker implements ApplicationRunner {

    /** 探测 Key（不存在，仅借用读命令触发建连，无副作用） */
    private static final String PROBE_KEY = "auth:blacklist:startup-probe";

    private final StringRedisTemplate redisTemplate;

    @Override
    public void run(ApplicationArguments args) {
        try {
            redisTemplate.hasKey(PROBE_KEY);
            log.info("Redis 连接正常（启动探测通过）");
        } catch (Exception e) {
            log.error("Redis 连接失败，Token 黑名单降级运行（fail-open），登出功能不可用", e);
        }
    }
}
