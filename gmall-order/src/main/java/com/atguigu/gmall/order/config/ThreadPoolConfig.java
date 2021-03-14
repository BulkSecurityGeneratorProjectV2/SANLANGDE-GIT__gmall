package com.atguigu.gmall.order.config;

import io.netty.util.concurrent.DefaultThreadFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Component
public class ThreadPoolConfig {

    @Bean
    public ThreadPoolExecutor threadPoolExecutor(
            @Value("${thread.pool.coreSize}") Integer coreSize,
            @Value("${thread.pool.maxSize}") Integer maxSize,
            @Value("${thread.pool.keepalive}") Integer keepalive,
            @Value("${thread.pool.blockQueueSize}") Integer blockQueueSize
    ){
        return new ThreadPoolExecutor(coreSize,maxSize,keepalive, TimeUnit.SECONDS,new ArrayBlockingQueue<>(blockQueueSize));
    }

}
