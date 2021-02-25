package com.atguigu.gmall.search.listener;

import com.atguigu.gmall.search.service.SearchService;
import com.rabbitmq.client.Channel;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class SpuInfoListener {

    @Autowired
    private SearchService searchService;

    /**
     * 处理insert的消息
     *
     * @param id
     * @throws Exception
     */
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = "item_spu_queue", durable = "true"),
            exchange = @Exchange(
                    value = "item_exchange",
                    ignoreDeclarationExceptions = "true",
                    type = ExchangeTypes.TOPIC),
            key = {"item.insert"}))
    public void listenCreate(Long id, Channel channel, Message message) throws Exception {
        if (id == null) {
            channel.basicReject(message.getMessageProperties().getDeliveryTag(),false);
            return;
        }
        // 创建索引
        this.searchService.createIndex(id);
        channel.basicAck(message.getMessageProperties().getDeliveryTag(),false);
    }
}

