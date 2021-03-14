package com.atguigu.gmall.oms.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;

@Slf4j
@Configuration
public class RabbitMqConfig {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @PostConstruct
    public void init(){
        this.rabbitTemplate.setConfirmCallback((correlationData, ack, cause)->{
            if(!ack){
                log.error("消息没有成功到达交换机，失败原因：{}",cause);
            }
        });
        this.rabbitTemplate.setReturnCallback((message, replyCode, replyText, exchange, routingKey)->{
            log.error("消息么有到达队列，交换机：{}，路由键：{}，消息内容：{}",exchange,routingKey,message);
        });
    }

    /**
     * 声明延时交换机：ORDER_EXCHANGE
     */

    /**
     * 声明延时队列
     * @return
     */
    @Bean
    public Queue orderDelayQueue(){
        return  QueueBuilder.durable("ORDER_DELAY_QUEUE")
                .withArgument("x-message-ttl", 60 * 1000)//生产环境30*60*1000 毫秒
                .withArgument("x-dead-letter-exchange", "ORDER_EXCHANGE")
                .withArgument("x-dead-letter-routing-key", "order.dead")
                .build();
    }

    /**
     * 延时队列绑定到延时交换机：order.close
     */
    @Bean
    public Binding delayBinding(){
        return new Binding(
                "ORDER_DELAY_QUEUE",
                Binding.DestinationType.QUEUE,
                "ORDER_EXCHANGE",
                "order.close",null);
    }
    /**
     * 声明死信交换机：ORDER_EXCHANGE
     */

    /**
     * 声明死信队列：ORDER_DEAD_QUEUE
     */
    @Bean
    public Queue orderDeadQueue(){
        return QueueBuilder.durable("ORDER_DEAD_QUEUE").build();
    }
    /**
     * 死信队列绑定到死信交换机：order.dead
     */
    @Bean
    public Binding deadBinding(){
        return new Binding(
                "ORDER_DEAD_QUEUE",
                Binding.DestinationType.QUEUE,
                "ORDER_EXCHANGE",
                "order.dead",null);
    }

}
