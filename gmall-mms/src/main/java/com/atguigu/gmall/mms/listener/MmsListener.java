package com.atguigu.gmall.mms.listener;

import com.atguigu.gmall.mms.service.MmsService;
import com.rabbitmq.client.Channel;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class MmsListener {

    @Autowired
    private MmsService mmsService;

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = "ums_register_queue",durable = "true"),
            exchange = @Exchange(value = "ums_register_exchange",ignoreDeclarationExceptions = "true",type = ExchangeTypes.TOPIC),
            key = {"ums.register"}
    ))
    public void sendMessage(String phone, Channel channel, Message message) throws IOException {
        if(phone==null){
            channel.basicReject(message.getMessageProperties().getDeliveryTag(),false);
            return;
        }
        this.mmsService.saveMsg(phone);
    }

}
