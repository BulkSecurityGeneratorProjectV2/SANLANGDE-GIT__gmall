package com.atguigu.gmall.index.config;

import java.lang.annotation.*;

@Target({ElementType.METHOD}) //注解作用在方法上
@Retention(RetentionPolicy.RUNTIME) //运行时注解
@Documented
public @interface GmallCache {

    /**
     * 缓存前缀
     * @return
     */
    String prefix() default  "";

    /**
     * 缓存过期时间
     * @return
     */
    int timeout() default 5;

    /**
     * 防止缓存雪崩，缓存随机时间
     * @return
     */
    int random() default 5;

    /**
     * 防止缓存击穿，添加锁的前缀
     * @return
     */
    String lock() default "lock";
}
