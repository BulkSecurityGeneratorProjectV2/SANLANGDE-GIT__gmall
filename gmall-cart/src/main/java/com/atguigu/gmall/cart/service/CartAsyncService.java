package com.atguigu.gmall.cart.service;

import com.atguigu.gmall.cart.entity.Cart;
import com.atguigu.gmall.cart.mapper.CartMapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class CartAsyncService {

    @Autowired
    private CartMapper cartMapper;

    @Async
    public void updateCartByUserIdAndSkuId(String userId, Cart cart){
        this.cartMapper.update(cart,new UpdateWrapper<Cart>().eq("user_id",userId).eq("sku_id",cart.getSkuId()));
    }

    @Async
    public void saveCart(String userId,Cart cart){
        this.cartMapper.insert(cart);
    }

    @Async
    public void deleteCart(String userId) {
        this.cartMapper.delete(new UpdateWrapper<Cart>().eq("user_id",userId));
    }

    @Async
    public void deleteCartBySkuId(String userId, String skuId) {
        this.cartMapper.delete(new UpdateWrapper<Cart>().eq("user_id",userId).eq("sku_id",skuId));
    }
}
