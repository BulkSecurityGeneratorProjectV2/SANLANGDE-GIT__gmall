package com.atguigu.gmall.index.service;

import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.common.bean.ResponseVo;
import com.atguigu.gmall.index.config.GmallCache;
import com.atguigu.gmall.index.feign.GmallPmsClient;
import com.atguigu.gmall.index.utils.DistributeLock;
import com.atguigu.gmall.pms.entity.CategoryEntity;
import com.google.common.collect.Lists;
import io.jsonwebtoken.lang.Collections;
import org.apache.commons.lang.StringUtils;
import org.redisson.api.RCountDownLatch;
import org.redisson.api.RLock;
import org.redisson.api.RReadWriteLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
public class IndexService {

    @Autowired
    private GmallPmsClient pmsClient;
    @Autowired
    private StringRedisTemplate redisTemplate;
    @Autowired
    private DistributeLock distributeLock;
    @Autowired
    private RedissonClient redissonClient;

    private static final String KEY_PREFIX = "index:category:";

    public List<CategoryEntity> queryLvl1CategoryEntities() {
        ResponseVo<List<CategoryEntity>> responseVo = pmsClient.queryCategoryEntities(0l);
        return responseVo.getData();
    }

    @GmallCache(prefix = "index:category:",timeout = 43200,random = 7200,lock="index:cate:lock:")
    public List<CategoryEntity> queryLvl2CategoriesWithSub(Long pid) {
        ResponseVo<List<CategoryEntity>> categoriesResponseVo = pmsClient.queryCategoriesWithSub(pid);
        List<CategoryEntity> categoryEntities = categoriesResponseVo.getData();
        return categoryEntities;
    }

    public List<CategoryEntity> queryLvl2CategoriesWithSub2(Long pid) {

        String json = redisTemplate.opsForValue().get(KEY_PREFIX+pid);
        if(!StringUtils.isBlank(json)){
            return JSON.parseArray(json,CategoryEntity.class);
        }
        //防止缓存击穿，添加分布式锁
        RLock lock = this.redissonClient.getLock("index:cate:lock:" + pid);
        lock.lock();

        try {
            //再次查询缓存，因为在请求等待获取锁的过程中，可能其他的请求已经把数据放入缓存
            String json2 = redisTemplate.opsForValue().get(KEY_PREFIX+pid);
            if(!StringUtils.isBlank(json)){
                return JSON.parseArray(json2,CategoryEntity.class);
            }

            List<CategoryEntity> categoryEntities = pmsClient.queryCategoriesWithSub(pid).getData();
            // 没有要方法问的数据时，将空数据放入缓存，解决缓存穿透，为防止缓存中有大量空数据，给予较短的过期时间
            if(Collections.isEmpty(categoryEntities)) {
                redisTemplate.opsForValue().set(KEY_PREFIX+pid,JSON.toJSONString(categoryEntities),5, TimeUnit.MINUTES);
            }else {
                // 给过期时间添加随机数，解决缓存雪崩
                redisTemplate.opsForValue().set(KEY_PREFIX+pid,JSON.toJSONString(categoryEntities),30+new Random().nextInt(10),TimeUnit.DAYS);
            }

            return categoryEntities;
        } finally {
            lock.unlock();
        }
    }

    public void testLock() {

        RLock lock = redissonClient.getLock("lock");
        //加锁
        lock.lock();

        try {
            // 查询redis中的num值
            String value = this.redisTemplate.opsForValue().get("number");
            // 没有该值return
            if (StringUtils.isBlank(value)) {
                return;
            }
            // 有值就转成成int
            int num = Integer.parseInt(value);
            // 把redis中的num值+1
            this.redisTemplate.opsForValue().set("number", String.valueOf(++num));

//            try {
//                TimeUnit.SECONDS.sleep(100);
//            } catch (InterruptedException e) {
//                e.printStackTrace();
//            }

        } finally {
            //释放锁
            lock.unlock();
        }
    }
    public void testLock4() {

        String uuid=UUID.randomUUID().toString();
        Boolean lock = this.tryLock("lock", uuid, 30l);
        if(lock) {
            // 查询redis中的num值
            String value = this.redisTemplate.opsForValue().get("number");
            // 没有该值return
            if (StringUtils.isBlank(value)) {
                return;
            }
            // 有值就转成成int
            int num = Integer.parseInt(value);
            // 把redis中的num值+1
            this.redisTemplate.opsForValue().set("number", String.valueOf(++num));
            //测试自动续期
//            try {
//                TimeUnit.SECONDS.sleep(1000);
//            } catch (InterruptedException e) {
//                e.printStackTrace();
//            }

            //可重入测试
            //this.testSubLock(uuid);

            this.unlock("lock",uuid);

        }

    }

    private void testSubLock(String uuid){
        this.tryLock("lock",uuid,30l);
        System.out.println("测试可重入锁");
        this.unlock("lock",uuid);
    }

    private void unlock(String lockName, String uuid){
        String script = "if (redis.call('hexists', KEYS[1], ARGV[1]) == 0) then" +
                "    return nil;" +
                "end;" +
                "if (redis.call('hincrby', KEYS[1], ARGV[1], -1) > 0) then" +
                "    return 0;" +
                "else" +
                "    redis.call('del', KEYS[1]);" +
                "    return 1;" +
                "end;";
        // 这里之所以没有跟加锁一样使用 Boolean ,这是因为解锁 lua 脚本中，三个返回值含义如下：
        // 1 代表解锁成功，锁被释放
        // 0 代表可重入次数被减 1
        // null 代表其他线程尝试解锁，解锁失败
        Long result = this.redisTemplate.execute(new DefaultRedisScript<>(script, Long.class), Lists.newArrayList(lockName), uuid);
        // 如果未返回值，代表尝试解其他线程的锁
        if (result == null) {
            throw new IllegalMonitorStateException("attempt to unlock lock, not locked by lockName: "
                    + lockName + " with request: "  + uuid);
        }else if(result == 1){
            //解锁成功，取消定时任务
            timer.cancel();
        }
    }

    private Boolean tryLock(String lockName, String uuid, Long expire){
        String script = "if (redis.call('exists', KEYS[1]) == 0 or redis.call('hexists', KEYS[1], ARGV[1]) == 1) " +
                "then" +
                "    redis.call('hincrby', KEYS[1], ARGV[1], 1);" +
                "    redis.call('expire', KEYS[1], ARGV[2]);" +
                "    return 1;" +
                "else" +
                "   return 0;" +
                "end";
        if (!this.redisTemplate.execute(new DefaultRedisScript<>(script, Boolean.class), Arrays.asList(lockName), uuid, expire.toString())){
            try {
                // 没有获取到锁，重试
                Thread.sleep(200);
                tryLock(lockName, uuid, expire);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        renewExpire(lockName,uuid,30);
        // 获取到锁，返回true
        return true;
    }

    private Timer timer;

    public void renewExpire(String lockName,String uuid,Integer expire){

        String script="if(redis.call( 'hexists', KEYS[1], ARGV[1]) == 1) then " +
                "   return redis.call( 'expire', KEYS[1], ARGV[2]) " +
                "else " +
                "   return 0 " +
                "end";
        timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                redisTemplate.execute(new DefaultRedisScript<>(script,Boolean.class),Arrays.asList(lockName),uuid,expire.toString());
            }
        },expire * 1000 /3,expire * 1000 /3);

    }

    public void testLock3() {
        // 1. 从redis中获取锁,setnx
        String uuid = UUID.randomUUID().toString();
        Boolean lock = this.redisTemplate.opsForValue().setIfAbsent("lock", uuid, 3, TimeUnit.SECONDS);
        if (lock) {
            // 查询redis中的num值
            String value = this.redisTemplate.opsForValue().get("number");
            // 没有该值return
            if (StringUtils.isBlank(value)){
                return ;
            }
            // 有值就转成成int
            int num = Integer.parseInt(value);
            // 把redis中的num值+1
            this.redisTemplate.opsForValue().set("number", String.valueOf(++num));

            // 2. 释放锁 del
            String script = "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";
            this.redisTemplate.execute(new DefaultRedisScript<>(script,Boolean.class), Arrays.asList("lock"), uuid);
            //            if (StringUtils.equals(uuid, this.redisTemplate.opsForValue().get("num"))) {
            //                this.redisTemplate.delete("lock");
            //            }
        } else {
            // 3. 每隔1秒钟回调一次，再次尝试获取锁
            try {
                Thread.sleep(1000);
                testLock();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public void testLock2() {
        String uuid = UUID.randomUUID().toString();
        //加锁
        //使用uuid 防止误删
        //为了防止死锁，设置过期时间
        Boolean lock = this.redisTemplate.opsForValue().setIfAbsent("lock", uuid,3,TimeUnit.SECONDS);
        //重试
        if(!lock){
            try {
                Thread.sleep(1000);
                testLock2();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }else {
            String number = this.redisTemplate.opsForValue().get("number");
            if(StringUtils.isBlank(number)){
                return;
            }
            int num = Integer.parseInt(number);

            this.redisTemplate.opsForValue().set("number",String.valueOf(++num));
            //解锁
            //为了防止误删，判断是否是自己的锁
            String script="if(redis.call('get',KEYS[1])==ARGV[1]) then return redis.call('del',KEYS[1]) else return 0 end";
            //预加载：springdata-redis：会自动预加载
            this.redisTemplate.execute(new DefaultRedisScript<>(script,Boolean.class), Arrays.asList("lock"),uuid);

//            if(StringUtils.equals(uuid,this.redisTemplate.opsForValue().get("lock"))) {
//                this.redisTemplate.delete("lock");
//            }
        }
    }

    public void testWrite() {

        RReadWriteLock rwLock = this.redissonClient.getReadWriteLock("rwLock");

        rwLock.writeLock().lock(10,TimeUnit.SECONDS);
        System.out.println("写的业务操作");
        rwLock.writeLock().unlock();
    }

    public void testRead() {
        RReadWriteLock rwLock = this.redissonClient.getReadWriteLock("rwLock");

        rwLock.readLock().lock(10,TimeUnit.SECONDS);
        System.out.println("写的业务操作");
        rwLock.readLock().unlock();
    }

    public void testLatch() {

        RCountDownLatch latch = this.redissonClient.getCountDownLatch("latch");
        latch.trySetCount(3);
        try {
            latch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }finally {
        }
        //todo:等待解锁

    }

    public void testCountDown() {
        RCountDownLatch latch = this.redissonClient.getCountDownLatch("latch");
        //业务功能
        latch.countDown();
    }
}
