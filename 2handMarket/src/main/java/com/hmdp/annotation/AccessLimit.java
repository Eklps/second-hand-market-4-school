package com.hmdp.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 接口限流注解
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AccessLimit {

    /**
     * 限流时间范围（秒）
     */
    int seconds() default 60;

    /**
     * 限流时间范围内的最大访问次数
     */
    int maxCount() default 10;

    /**
     * 是否需要登录
     */
    boolean needLogin() default true;
}
