package com.paddleocr.web.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 防重复提交注解：标注在 Controller 写方法上，方法执行前以 Redis 分布式锁拦截重复请求
 *
 * <p>拦截口径：同一用户 + 同一接口 + 相同参数指纹，防重窗口内仅放行第一次请求，
 * 其余返回 40045；不同用户或不同参数互不影响，不误伤连续的正常提交。纯查询接口无需标注。</p>
 *
 * @author paddleocr
 * @date 2026-10-19
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RepeatSubmit {

    /**
     * 锁 key 业务前缀，缺省取「Controller 简单类名#方法名」
     */
    String prefix() default "";

    /**
     * 防重窗口秒数：方法成功后锁保留时长，到期自动释放（兼作死锁兜底，无需主动解锁）
     */
    long expireSeconds() default 5;
}
