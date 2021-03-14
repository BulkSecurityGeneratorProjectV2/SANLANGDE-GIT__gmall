package com.atguigu.gmall.scheduled.job;

import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.scheduled.entity.Cart;
import com.atguigu.gmall.scheduled.mapper.CartMapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.xxl.job.core.biz.model.ReturnT;
import com.xxl.job.core.handler.annotation.XxlJob;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.BoundHashOperations;
import org.springframework.data.redis.core.BoundSetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.List;

@Component
public class CartJobHandler {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private CartMapper cartMapper;

    private static final String EXCEPTION_KEY="cart:exception:info:";
    private static final String KEY_PREFIX="cart:info:";

    @XxlJob("cartJobHandler")
    public ReturnT<String> asyncCartData(String param){

        BoundSetOperations<String, String> setOps = this.redisTemplate.boundSetOps(EXCEPTION_KEY);
        if (setOps.size()==0) {
            return ReturnT.SUCCESS;
        }

        String userId = setOps.pop();
        while (StringUtils.isNotBlank(userId)){

            //1.先删除用户MySQL购物车对应的记录
            this.cartMapper.delete(new UpdateWrapper<Cart>().eq("user_id",userId));
            //2.查询redis中对应的购物车记录
            BoundHashOperations<String, Object, Object> hashOps = this.redisTemplate.boundHashOps(KEY_PREFIX + userId);
            //3.判断redis中购物车是否为空
            List<Object> cartJsons = hashOps.values();
            if (CollectionUtils.isEmpty(cartJsons)) {
                continue;
            }
            //4.不为空，同步数据到MySQL中
            cartJsons.forEach(cartJson->{
                Cart cart = JSON.parseObject(cartJson.toString(), Cart.class);
                this.cartMapper.insert(cart);
            });
            //下一个用户id
            userId = setOps.pop();
        }

        return ReturnT.SUCCESS;
    }


}
