package com.atguigu.gmall.cart.listener;

import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.cart.feign.GmallPmsClient;
import com.atguigu.gmall.common.bean.ResponseVo;
import com.atguigu.gmall.pms.entity.SkuEntity;
import com.rabbitmq.client.Channel;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.BoundHashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Component
public class CartListener {

    private static final String KEY_PRICE_PREFIX="cart:price:";

    private static final String KEY_CART_PREFIX="cart:info:";

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private GmallPmsClient pmsClient;

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = "cart_price_queue",durable = "true"),
            exchange = @Exchange(value = "CART_EXCHANGE",ignoreDeclarationExceptions = "true",type = ExchangeTypes.TOPIC),
            key={"cart.price"}
    ))
    public void listener(Long spuId, Channel channel, Message message) throws IOException {
        if(spuId==null){
            //拒收，禁止重新入队
            channel.basicAck(message.getMessageProperties().getDeliveryTag(),false);
            return;
        }
        ResponseVo<List<SkuEntity>> listResponseVo = this.pmsClient.querySkuListBySpuId(spuId);
        List<SkuEntity> skuEntities = listResponseVo.getData();
        //如果sku为空，直接确认消息，返回
        if(CollectionUtils.isEmpty(skuEntities)){
            channel.basicAck(message.getMessageProperties().getDeliveryTag(),false);
            return;
        }

        skuEntities.forEach(skuEntity -> {
            if (this.redisTemplate.hasKey(KEY_PRICE_PREFIX + skuEntity.getId())) {
                this.redisTemplate.opsForValue().set(KEY_PRICE_PREFIX + skuEntity.getId(),skuEntity.getPrice().toString());
            }
        });
        channel.basicAck(message.getMessageProperties().getDeliveryTag(),false);
    }

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = "CART_DELETE_QUEUE",durable = "true"),
            exchange = @Exchange(value = "CART_EXCHANGE",ignoreDeclarationExceptions = "true",type = ExchangeTypes.TOPIC),
            key={"cart.delete"}
    ))
    public void deleteCart(Map<String,Object> map, Channel channel, Message message) throws IOException {
        if(CollectionUtils.isEmpty(map)){
            //拒收，禁止重新入队
            channel.basicAck(message.getMessageProperties().getDeliveryTag(),false);
            return;
        }

        String userId = map.get("userId").toString();
        List<String> skuIds = JSON.parseArray(map.get("skuIds").toString(), String.class);
        BoundHashOperations<String, Object, Object> hashOps = this.redisTemplate.boundHashOps(KEY_CART_PREFIX + userId);
        hashOps.delete(skuIds.toArray());
            channel.basicAck(message.getMessageProperties().getDeliveryTag(),false);

        /*try {
            String userId = map.get("userId").toString();
            String skuIdString = map.get("skuIds").toString();
            List<String> skuIds = JSON.parseArray(skuIdString, String.class);

            BoundHashOperations<String, Object, Object> hashOps = this.redisTemplate.boundHashOps(KEY_CART_PREFIX + userId);
            hashOps.delete(skuIds.toArray());

            channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
        } catch (Exception e) {
            e.printStackTrace();
            if (message.getMessageProperties().getRedelivered()){
                channel.basicReject(message.getMessageProperties().getDeliveryTag(), false);
            } else {
                channel.basicNack(message.getMessageProperties().getDeliveryTag(), false, true);
            }
        }*/
    }

}
