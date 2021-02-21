package com.atguigu.gmall.index.config;

import com.atguigu.gmall.common.bean.ResponseVo;
import com.atguigu.gmall.index.feign.GmallPmsClient;
import com.atguigu.gmall.pms.entity.CategoryEntity;
import io.jsonwebtoken.lang.Collections;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class BloomFilterConfig {
    @Autowired
    private RedissonClient redissonClient;
    @Autowired
    private GmallPmsClient pmsClient;

    private static final String KEY_PREFIX="index:category:";

    @Bean
    public RBloomFilter rBloomFilter(){
        // 初始化布隆过滤器
        RBloomFilter<String> bloomFilter = this.redissonClient.getBloomFilter("bloomfilter");
        bloomFilter.tryInit(50l, 0.03);

        ResponseVo<List<CategoryEntity>> listResponseVo = this.pmsClient.queryCategoryEntities(0l);
        List<CategoryEntity> categoryEntities = listResponseVo.getData();
        if (!Collections.isEmpty(categoryEntities)){
            categoryEntities.forEach(categoryEntity -> {
                bloomFilter.add(categoryEntity.getId().toString());
            });
        }
        return bloomFilter;
    }
}
