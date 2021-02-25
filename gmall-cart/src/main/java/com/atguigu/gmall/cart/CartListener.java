package com.atguigu.gmall.cart;

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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.io.IOException;
import java.util.List;

@Component
public class CartListener {

    private static final String KEY_PRICE_PREFIX="cart:price:";

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private GmallPmsClient pmsClient;

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = "cart_price_queue",durable = "true"),
            exchange = @Exchange(value = "cart_exchange",ignoreDeclarationExceptions = "true",type = ExchangeTypes.TOPIC),
            key={"cart.update"}
    ))
    public void listener(Long spuId, Channel channel, Message message) throws IOException {
        if(spuId==null){
            //拒收，禁止重新入队
            channel.basicReject(message.getMessageProperties().getDeliveryTag(),false);
            return;
        }
        ResponseVo<List<SkuEntity>> listResponseVo = this.pmsClient.querySkuListBySpuId(spuId);
        List<SkuEntity> skuEntities = listResponseVo.getData();
        //如果sku为空，直接确认消息，返回
        if(CollectionUtils.isEmpty(skuEntities)){
            channel.basicReject(message.getMessageProperties().getDeliveryTag(),false);
            return;
        }

        skuEntities.forEach(skuEntity -> {
            if (this.redisTemplate.hasKey(KEY_PRICE_PREFIX + skuEntity.getId())) {
                this.redisTemplate.opsForValue().set(KEY_PRICE_PREFIX + skuEntity.getId(),skuEntity.getPrice().toString());
            }
        });
        channel.basicAck(message.getMessageProperties().getDeliveryTag(),false);
    }

}
