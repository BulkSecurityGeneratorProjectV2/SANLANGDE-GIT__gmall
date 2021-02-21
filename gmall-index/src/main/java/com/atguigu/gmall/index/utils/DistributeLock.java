package com.atguigu.gmall.index.utils;

import com.google.common.collect.Lists;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.TimeoutUtils;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

@Component
public class DistributeLock {

    @Autowired
    private StringRedisTemplate redisTemplate;

//    public static void main(String[] args) {
//        System.out.println("定时任务初始化时间："+System.currentTimeMillis());
//        new Timer().schedule(new TimerTask() {
//            @Override
//            public void run() {
//                System.out.println("jdk的定时任务："+System.currentTimeMillis());
//            }
//        },5000,10000);
//    }

//
//    public Boolean tryLock(String lockName,String uuid,Long expire){
////        String script="if (redis.call( 'exists', KEYS[1]) == 0 or redis.call( 'hexists', KEYS[1], ARGV[1])) then" +
////                "   redis.call( 'hincrby', KEYS[1], ARGV[1], 1);" +
////                "   redis.call( 'expire', KEYS[1], ARGV[2]);" +
////                "   return 1;" +
////                "else " +
////                "   return 0;" +
////                "end";
//
//        String script = "if (redis.call('exists', KEYS[1]) == 0 or redis.call('hexists', KEYS[1], ARGV[1]) == 1) " +
//                "then" +
//                "    redis.call('hincrby', KEYS[1], ARGV[1], 1);" +
//                "    redis.call('expire', KEYS[1], ARGV[2]);" +
//                "    return 1;" +
//                "else" +
//                "   return 0;" +
//                "end";
//
//        if(!this.redisTemplate.execute(new DefaultRedisScript<>(script,Boolean.class), Arrays.asList(lockName),uuid,expire.toString())){
//            try {
//                Thread.sleep(50);
//                tryLock(lockName, uuid, expire);
//            } catch (InterruptedException e) {
//                e.printStackTrace();
//            }
//        }
//        //获取到锁，返回true
//        return true;
//    }
//
//    public void unlock(String lockName,String uuid){
//        //返回值为null代表要解的锁不存在，或者是解别人的锁
//        //返回0代表出来一次
//        //返回1代表解锁成功
//        String script="if ( redis.call( 'hexists', KEYS[1], ARGV[1]) == 0) then " +
//                "   return nil;" +
//                "elseif ( redis.call( 'hincrby', KEYS[1], ARGV[1], -1) == 0 ) then " +
//                "   return redis.call('del',KEYS[1]);" +
//                "else " +
//                "   return 0;" +
//                "end";
//
////        String script = "if (redis.call('hexists', KEYS[1], ARGV[1]) == 0) then" +
////                "    return nil;" +
////                "end;" +
////                "if (redis.call('hincrby', KEYS[1], ARGV[1], -1) > 0) then" +
////                "    return 0;" +
////                "else" +
////                "    redis.call('del', KEYS[1]);" +
////                "    return 1;" +
////                "end;";
//        Long flag = this.redisTemplate.execute(new DefaultRedisScript<>(script, Long.class), Arrays.asList(lockName), uuid);
//
//        //Long flag = this.redisTemplate.execute(new DefaultRedisScript<>(script, Long.class), Lists.newArrayList(lockName), uuid);
//
//        if(flag == null){
//            log.error("要解的锁不存在，或者再尝试解别人的锁。锁的名称：{},锁的uuid：{}",lockName,uuid);
////            throw new IllegalMonitorStateException("attempt to unlock lock, not locked by lockName: "
////                    + lockName + " with request: "  + uuid);
//        }
//
//    }

}
