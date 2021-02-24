package com.atguigu.gmall.cart.service;

import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.cart.entity.Cart;
import com.atguigu.gmall.cart.entity.UserInfo;
import com.atguigu.gmall.cart.feign.GmallPmsClient;
import com.atguigu.gmall.cart.feign.GmallSmsClient;
import com.atguigu.gmall.cart.feign.GmallWmsClient;
import com.atguigu.gmall.cart.interceptor.LoginInterceptor;
import com.atguigu.gmall.cart.mapper.CartMapper;
import com.atguigu.gmall.common.bean.ResponseVo;
import com.atguigu.gmall.common.exception.CartException;
import com.atguigu.gmall.pms.entity.SkuAttrValueEntity;
import com.atguigu.gmall.pms.entity.SkuEntity;
import com.atguigu.gmall.sms.vo.ItemSaleVo;
import com.atguigu.gmall.wms.entity.WareSkuEntity;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.BoundHashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.AsyncResult;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.concurrent.ListenableFuture;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class CartService {

    private static final String KEY_PREFIX="cart:info:";

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private GmallPmsClient pmsClient;
    @Autowired
    private GmallSmsClient smsClient;
    @Autowired
    private GmallWmsClient wmsClient;
    @Autowired
    private CartAsyncService cartAsyncService;

    public void saveCart(Cart cart) {
        //1.获取用户的登录信息
        String userId = this.getUserId();
        // 获取当前用户的购物车
        BoundHashOperations<String, Object, Object> hashOps = this.redisTemplate.boundHashOps(KEY_PREFIX + userId);
        Long skuId = cart.getSkuId();
        BigDecimal count = cart.getCount();
        if (hashOps.hasKey(skuId.toString())) {
            //判断用户的购物车是否包含该商品
            //包含：更新数量
            String cartJson = hashOps.get(skuId.toString()).toString();
            cart = JSON.parseObject(cartJson, Cart.class);
            cart.setCount(cart.getCount().add(count));
            this.cartAsyncService.updateCartByUserIdAndSkuId(userId,cart);
        }else {
            // 不包含：新增一条记录
            ResponseVo<SkuEntity> skuEntityResponseVo = pmsClient.querySkuById(skuId);
            SkuEntity skuEntity = skuEntityResponseVo.getData();
            if(skuEntity==null){
                return;
            }
            cart.setUserId(userId);
            cart.setSkuId(skuId);
            cart.setCheck(true);
            cart.setTitle(skuEntity.getTitle());
            cart.setDefaultImage(skuEntity.getDefaultImage());
            cart.setPrice(skuEntity.getPrice());
            cart.setCount(count);
            //库存
            ResponseVo<List<WareSkuEntity>> listResponseVo = this.wmsClient.queryWareSkuEntitiesBySkuId(skuId);
            List<WareSkuEntity> wareSkuEntities = listResponseVo.getData();
            if(!CollectionUtils.isEmpty(wareSkuEntities))
                cart.setStore(wareSkuEntities.stream().anyMatch(wareSkuEntity -> wareSkuEntity.getStock()-wareSkuEntity.getStockLocked() > 0));
            //销售属性
            ResponseVo<List<SkuAttrValueEntity>> responseVo = this.pmsClient.querySaleAttrBySkuId(skuId);
            List<SkuAttrValueEntity> attrValueEntities = responseVo.getData();
            cart.setSaleAttrs(JSON.toJSONString(attrValueEntities));
            //营销信息
            ResponseVo<List<ItemSaleVo>> itemResponseVo = this.smsClient.querySalesBySkuId(skuId);
            List<ItemSaleVo> itemSaleVos = itemResponseVo.getData();
            cart.setSales(JSON.toJSONString(itemSaleVos));

            this.cartAsyncService.saveCart(userId,cart);
        }
        hashOps.put(skuId.toString(),JSON.toJSONString(cart));
    }

    private String getUserId() {
        UserInfo userInfo = LoginInterceptor.getUserInfo();
        if(userInfo.getUserId()==null){
            return userInfo.getUserKey();
        }else {
            return userInfo.getUserId().toString();
        }
    }

    public Cart queryCartBySkuId(Long skuId) {
        String userId = this.getUserId();
        BoundHashOperations<String, Object, Object> boundHashOps = this.redisTemplate.boundHashOps(KEY_PREFIX + userId);
        if(boundHashOps.hasKey(skuId.toString())){
            String cartJson = boundHashOps.get(skuId.toString()).toString();
            return JSON.parseObject(cartJson,Cart.class);
        }
        throw new CartException("此用的购物车不包含该记录！！！");
    }

    @Async
    public void executor2() {
        System.out.println("executor2()方法开始执行");
        try {
            TimeUnit.SECONDS.sleep(5);
            int i=1/0;
            System.out.println("executor2()方法执行结束----------------");
            //return AsyncResult.forValue("hello executor2");
        } catch (InterruptedException e) {
            //e.printStackTrace();
            //return AsyncResult.forExecutionException(e);
        }
    }
    @Async
    public void executor1() {
        System.out.println("executor1()方法开始执行");
        try {
            TimeUnit.SECONDS.sleep(4);
            System.out.println("executor1()方法执行结束----------------");
            //return AsyncResult.forValue("hello executor1()");
        } catch (InterruptedException e) {
            //return AsyncResult.forExecutionException(e);
        }
    }

    public List<Cart> queryCarts() {
        //1.获取用登录的信息
        //2.判断是否有未登录购物城记录
        //3.
        return null;
    }
}
