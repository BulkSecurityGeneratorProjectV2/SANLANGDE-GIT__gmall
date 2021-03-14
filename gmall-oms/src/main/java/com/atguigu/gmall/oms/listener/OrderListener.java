package com.atguigu.gmall.oms.listener;

import com.atguigu.gmall.oms.mapper.OrderMapper;
import com.rabbitmq.client.Channel;
import org.apache.commons.lang3.StringUtils;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class OrderListener {

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = "ORDER_DISABLE_QUEUE",durable = "true"),
            exchange = @Exchange(value = "ORDER_EXCHANGE",ignoreDeclarationExceptions = "true",type = ExchangeTypes.TOPIC),
            key={"stock.unlock"}
    ))
    public void disableOrder(String orderToken, Channel channel, Message message) throws IOException {

        if(StringUtils.isBlank(orderToken)){
            channel.basicAck(message.getMessageProperties().getDeliveryTag(),false);
            return;
        }
        //更新订单的状态未无效订单
        orderMapper.updateStatus(orderToken,0,5);
        channel.basicAck(message.getMessageProperties().getDeliveryTag(),false);

    }

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = "ORDER_SUCCESS_QUEUE",durable = "true"),
            exchange = @Exchange(value = "ORDER_EXCHANGE",ignoreDeclarationExceptions = "true",type = ExchangeTypes.TOPIC),
            key={"order.success"}
    ))
    public void successOrder(String orderToken, Channel channel, Message message) throws IOException {
        if(StringUtils.isBlank(orderToken)){
            channel.basicAck(message.getMessageProperties().getDeliveryTag(),false);
            return;
        }
        //更新订单的状态为代发货状态
        if (orderMapper.updateStatus(orderToken, 0, 1)==1) {
            //订单支付成功，真正的减库存
            this.rabbitTemplate.convertAndSend("ORDER_EXCHANGE","stock.minus",orderToken);
            //TODO:给用户添加积分
            channel.basicAck(message.getMessageProperties().getDeliveryTag(),false);
        }
    }

    @RabbitListener(queues = {"ORDER_DEAD_QUEUE"})
    public void closeOrder(String orderToken,Channel channel,Message message) throws IOException {
        try {
            //更新订单的关闭状态:4
            if (orderMapper.updateStatus(orderToken,0,4)==1) {
                //解锁库存
                this.rabbitTemplate.convertAndSend("ORDER_EXCHANGE","stock.unlock",orderToken);
            }
            channel.basicAck(message.getMessageProperties().getDeliveryTag(),false);
        } catch (IOException e) {
            e.printStackTrace();
            if (message.getMessageProperties().getRedelivered()){
                channel.basicReject(message.getMessageProperties().getDeliveryTag(), false);
            } else {
                channel.basicNack(message.getMessageProperties().getDeliveryTag(), false, true);
            }
        }
    }

}
