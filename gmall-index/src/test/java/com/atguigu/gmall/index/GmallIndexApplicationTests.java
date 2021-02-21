package com.atguigu.gmall.index;

import org.junit.jupiter.api.Test;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Timer;
import java.util.TimerTask;

@SpringBootTest
class GmallIndexApplicationTests {
    @Autowired
    private RedissonClient redissonClient;
    @Test
    void contextLoads() {
        RBloomFilter<Object> bloomFilter = this.redissonClient.getBloomFilter("bloomFilter");

        bloomFilter.tryInit(5000,0.3);

        bloomFilter.add("1");
        bloomFilter.add("2");
        bloomFilter.add("3");
        bloomFilter.add("4");
        bloomFilter.add("5");

        System.out.println(bloomFilter.contains("2"));
        System.out.println(bloomFilter.contains("4"));
        System.out.println(bloomFilter.contains("6"));
        System.out.println(bloomFilter.contains("8"));
        System.out.println(bloomFilter.contains("10"));
        System.out.println(bloomFilter.contains("12"));
        System.out.println(bloomFilter.contains("14"));

    }

}
