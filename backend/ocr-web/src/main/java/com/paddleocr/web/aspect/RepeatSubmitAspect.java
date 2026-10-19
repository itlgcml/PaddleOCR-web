package com.paddleocr.web.aspect;

import com.paddleocr.web.annotation.RepeatSubmit;
import com.paddleocr.web.common.BusinessException;
import com.paddleocr.web.common.ResultCode;
import com.paddleocr.web.security.SecurityUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;
import org.springframework.util.DigestUtils;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

/**
 * 防重复提交切面：@RepeatSubmit 标注的 Controller 方法执行前以 Redisson 锁拦截重复请求
 *
 * <p>锁 key = 前缀（缺省 Controller#方法名）+ 用户 ID + 参数指纹（MD5），
 * 同一用户对同一接口提交相同内容才互斥；未登录接口（注册/登录/刷新）按匿名口径拦截。</p>
 *
 * <p>锁语义与 Service 层手写防重锁一致：tryLock 不等待（重复请求立即 40045），
 * leaseTime 兜底自动释放防死锁；方法成功后不主动解锁——防重窗口完整覆盖 expireSeconds；
 * 方法失败立即解锁，允许用户修正后马上重试。</p>
 *
 * <p>注解统一织入于 Controller 层，天然位于 Service 层事务边界之外，
 * 不与既有业务级锁（流程提交锁/实例推进锁/单据发起锁）冲突，二者互补：
 * 本切面拦同用户同参数的重复请求，业务锁拦跨用户并发的互斥写。</p>
 *
 * @author paddleocr
 * @date 2026-10-19
 */
@Aspect
@Component
public class RepeatSubmitAspect {

    private static final String LOCK_KEY_PREFIX = "web:repeat_submit:";

    /** 未登录接口（注册/登录/刷新）的匿名用户标识 */
    private static final String ANONYMOUS_USER = "anonymous";

    private final RedissonClient redissonClient;

    public RepeatSubmitAspect(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    /**
     * 环绕拦截：抢锁 → 执行原方法 → 成功保留锁至过期 / 失败立即释放
     */
    @Around("@annotation(repeatSubmit)")
    public Object around(ProceedingJoinPoint joinPoint, RepeatSubmit repeatSubmit) throws Throwable {
        RLock lock = redissonClient.getLock(buildLockKey(joinPoint, repeatSubmit));
        boolean locked = false;
        try {
            // 不等待：并发重复请求立即失败；leaseTime 兜底自动释放，进程崩溃也不会死锁
            locked = lock.tryLock(0, repeatSubmit.expireSeconds(), TimeUnit.SECONDS);
            if (!locked) {
                throw new BusinessException(ResultCode.PROCESS_REPEAT_SUBMIT);
            }
            Object result = joinPoint.proceed();
            // 成功不主动解锁：防重窗口完整覆盖 expireSeconds（对齐 Service 层手写锁语义）
            return result;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ResultCode.PROCESS_REPEAT_SUBMIT);
        } catch (Throwable e) {
            // 失败立即释放：允许用户修正后马上重试
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
            throw e;
        }
    }

    /**
     * 组装锁 key：前缀（缺省 Controller#方法名）+ 用户 + 参数指纹
     */
    private String buildLockKey(ProceedingJoinPoint joinPoint, RepeatSubmit repeatSubmit) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String prefix = repeatSubmit.prefix().isEmpty()
                ? signature.getDeclaringType().getSimpleName() + "#" + signature.getName()
                : repeatSubmit.prefix();
        Long userId = SecurityUtils.tryGetUserId();
        String user = userId == null ? ANONYMOUS_USER : String.valueOf(userId);
        return LOCK_KEY_PREFIX + prefix + ":" + user + ":"
                + DigestUtils.md5DigestAsHex(argsFingerprint(joinPoint.getArgs()).getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 参数指纹：deepToString 展开（DTO 依赖 lombok toString），Servlet API / MultipartFile 以类名占位
     */
    private String argsFingerprint(Object[] args) {
        if (args == null || args.length == 0) {
            return "()";
        }
        Object[] normalized = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            normalized[i] = (args[i] instanceof ServletRequest || args[i] instanceof ServletResponse
                    || args[i] instanceof MultipartFile)
                    ? args[i].getClass().getSimpleName() : args[i];
        }
        return Arrays.deepToString(normalized);
    }
}
