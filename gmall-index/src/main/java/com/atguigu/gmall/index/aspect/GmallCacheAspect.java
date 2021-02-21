package com.atguigu.gmall.index.aspect;

import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.index.config.BloomFilterConfig;
import com.atguigu.gmall.index.config.GmallCache;
import com.atguigu.gmall.pms.entity.CategoryEntity;
import org.apache.commons.lang.StringUtils;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

@Aspect
@Component
public class GmallCacheAspect {

    @Autowired
    private StringRedisTemplate redisTemplate;
    @Autowired
    private RedissonClient redissonClient;
    @Autowired
    private RBloomFilter bloomFilter;

    @Around("@annotation(com.atguigu.gmall.index.config.GmallCache)")
    public Object before(ProceedingJoinPoint joinPoint){

        try {
            //获取方法签名
            MethodSignature signature=(MethodSignature)joinPoint.getSignature();
            //获取方法对象
            Method method = signature.getMethod();
            //获取返回值类型
            Class returnType = signature.getReturnType();
            //获取注解对象
            GmallCache gmallCache = method.getAnnotation(GmallCache.class);
            String prefix = gmallCache.prefix();
            //获取方法参数，返回的是数组，而数组的toString是地址
            List<Object> args = Arrays.asList(joinPoint.getArgs());
            String key=prefix+args;

            if(!this.bloomFilter.contains(args.get(0).toString())){
                return null;
            }

            //1、先查询缓存，如果缓存命中，直接返回
            String json = this.redisTemplate.opsForValue().get(key);
            if(!StringUtils.isBlank(json)){
                return JSON.parseObject(json,returnType);
            }
            //2、防止缓存击穿，添加分布式锁
            String lock = gmallCache.lock();
            RLock fairLock = this.redissonClient.getFairLock(lock + args);
            fairLock.lock();
            try {
                //3、再次查询缓存，命中直接返回
                String json2 = this.redisTemplate.opsForValue().get(key);
                if(!StringUtils.isBlank(json)){
                    return JSON.parseObject(json2,returnType);
                }
                //4、执行目标方法，远程调用或者从数据库查询数据
                //把数据放入缓存
                Object result = joinPoint.proceed(joinPoint.getArgs());
                int timeout = gmallCache.timeout()+new Random().nextInt(gmallCache.random());
                if(result!=null){
                    this.redisTemplate.opsForValue().set(key,JSON.toJSONString(result),timeout, TimeUnit.MINUTES);
                }
//            else {
//                //防止缓存穿透，缓存为null的数据
//                this.redisTemplate.opsForValue().set(key,JSON.toJSONString(result),5, TimeUnit.MINUTES);
//            }
                return result;
            } finally {
                fairLock.unlock();
            }
        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }
        return null;
    }

}
