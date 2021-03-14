package com.atguigu.gmall.wms.listener;

import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.wms.mapper.WareSkuMapper;
import com.atguigu.gmall.wms.vo.SkuLockVo;
import com.rabbitmq.client.Channel;
import org.apache.commons.lang3.StringUtils;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

@Component
public class StockListener {
    
    @Autowired
    private WareSkuMapper wareSkuMapper;
    
    @Autowired
    private StringRedisTemplate redisTemplate;
    //所库存缓存前缀
    private static  final String STOCK_LOCK_INFO="stock:lock:info:";

//    @RabbitListener(queues = {"STOCK_DEAD_QUEUE"})
//    public void stockDelayUnlock(String orderToken, Channel channel, Message message) throws IOException
//    {
//        try {
//            //获取redis订单锁定的库存信息
//            String json = this.redisTemplate.opsForValue().get(STOCK_LOCK_INFO + orderToken);
//            if(StringUtils.isNotBlank(json)) {
//                //反序列化锁定的库存列表
//                List<SkuLockVo> lockVos = JSON.parseArray(json, SkuLockVo.class);
//                //循环解锁库存
//                lockVos.forEach(lockVo -> {
//                    this.wareSkuMapper.unlock(lockVo.getWareSkuId(),lockVo.getCount());
//                });
//                //解锁库存库存之后删除redis中缓存的信息
//                this.redisTemplate.delete(STOCK_LOCK_INFO + orderToken);
//                channel.basicAck(message.getMessageProperties().getDeliveryTag(),false);
//            }
//        } catch (Exception e) {
//            e.printStackTrace();
//            if (message.getMessageProperties().getRedelivered()){
//                channel.basicReject(message.getMessageProperties().getDeliveryTag(), false);
//            } else {
//                channel.basicNack(message.getMessageProperties().getDeliveryTag(), false, true);
//            }
//        }
//    }

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = "ORDER_UNLOCK_QUEUE",durable = "true"),
            exchange = @Exchange(value = "ORDER_EXCHANGE",ignoreDeclarationExceptions = "true",type = ExchangeTypes.TOPIC),
            key={"stock.unlock"}
    ))
    public void stockUnlock(String orderToken, Channel channel, Message message) throws IOException {
        if(StringUtils.isBlank(orderToken)){
            channel.basicAck(message.getMessageProperties().getDeliveryTag(),false);
            return;
        }
        //获取redis订单锁定的库存信息
        String json = this.redisTemplate.opsForValue().get(STOCK_LOCK_INFO + orderToken);
        if(StringUtils.isNotBlank(json)) {
            //反序列化锁定的库存列表
            List<SkuLockVo> lockVos = JSON.parseArray(json, SkuLockVo.class);
            //循环解锁库存
            lockVos.forEach(lockVo -> {
                this.wareSkuMapper.unlock(lockVo.getWareSkuId(),lockVo.getCount());
            });
            //解锁库存库存之后删除redis中缓存的信息,防止重复解锁
            this.redisTemplate.delete(STOCK_LOCK_INFO + orderToken);
        }
        channel.basicAck(message.getMessageProperties().getDeliveryTag(),false);

    }

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = "ORDER_MINUS_QUEUE",durable = "true"),
            exchange = @Exchange(value = "ORDER_EXCHANGE",ignoreDeclarationExceptions = "true",type = ExchangeTypes.TOPIC),
            key={"stock.minus"}
    ))
    public void stockMinus(String orderToken, Channel channel, Message message) throws IOException {

        //获取redis订单锁定的库存信息
        String json = this.redisTemplate.opsForValue().get(STOCK_LOCK_INFO + orderToken);
        if(StringUtils.isBlank(json)) {
            channel.basicAck(message.getMessageProperties().getDeliveryTag(),false);
            return;
        }
        //反序列化锁定的库存列表
        List<SkuLockVo> lockVos = JSON.parseArray(json, SkuLockVo.class);
        //循环解锁库存
        lockVos.forEach(lockVo -> {
            this.wareSkuMapper.minus(lockVo.getWareSkuId(),lockVo.getCount());
        });
        //解锁库存库存之后删除redis中缓存的信息,防止重复解锁
        this.redisTemplate.delete(STOCK_LOCK_INFO + orderToken);
        channel.basicAck(message.getMessageProperties().getDeliveryTag(),false);

    }

}
