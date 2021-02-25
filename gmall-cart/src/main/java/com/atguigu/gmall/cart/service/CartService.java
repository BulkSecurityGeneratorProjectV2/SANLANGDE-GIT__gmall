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
import org.apache.commons.lang3.StringUtils;
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
import java.util.stream.Collectors;

@Service
public class CartService {

    private static final String KEY_PREFIX="cart:info:";

    private static final String KEY_PRICE_PREFIX="cart:price:";

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

    /**
     * 新增购物车
     * @param cart
     */
    public void saveCart(Cart cart) {
        //1.获取用户的登录信息
        String userId = this.getUserId();
        // 获取当前用户的购物车:redis缓存中
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

            this.redisTemplate.opsForValue().set(KEY_PRICE_PREFIX + skuId.toString(),skuEntity.getPrice().toString());
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

    /**
     * 查询购物车
     * @return
     */
    public List<Cart> queryCarts() {
        //1.获取用户登录的信息userKey
        UserInfo userInfo = LoginInterceptor.getUserInfo();
        String userKey = userInfo.getUserKey();
        String unLoginKey=KEY_PREFIX + userKey;
        //2.根据userKey查询未登录购物城记录
        //获取未登录购物车的内层map
        BoundHashOperations<String, Object, Object> unHashMap = this.redisTemplate.boundHashOps(unLoginKey);
        List<Object> unLoginCartJsons = unHashMap.values();
        List<Cart> unLoginCarts =null;
        if(!CollectionUtils.isEmpty(unLoginCartJsons)){
            unLoginCarts =unLoginCartJsons.stream().map(unLoginCart-> {
                Cart cart = JSON.parseObject(unLoginCart.toString(), Cart.class);
                cart.setCurrentPrice(new BigDecimal(this.redisTemplate.opsForValue().get(KEY_PRICE_PREFIX + cart.getSkuId())));
                return cart;
            }).collect(Collectors.toList());
        }
        //3.获取userId,如果为空直接返回未登录的购物车
        Long userId = userInfo.getUserId();
        if(userId==null){
            return unLoginCarts;
        }
        //4.合并登录购物车和未登录购物车
        String loginKey=KEY_PREFIX + userId;
        BoundHashOperations<String, Object, Object> loginHashOps = this.redisTemplate.boundHashOps(loginKey);
        if(!CollectionUtils.isEmpty(unLoginCarts)){
            unLoginCarts.forEach(cart -> {
                BigDecimal count = cart.getCount();
                String skuId = cart.getSkuId().toString();
                if(loginHashOps.hasKey(skuId)){
                    //用户购物车包含了该记录，合并数量
                    String cartJson = loginHashOps.get(skuId).toString();
                    cart = JSON.parseObject(cartJson,Cart.class);
                    cart.setCount(cart.getCount().add(count));
                    //异步写入MySQL
                    this.cartAsyncService.updateCartByUserIdAndSkuId(userId.toString(),cart);
                }else {
                    //用户的购物车不包含该记录，新增记录
                    cart.setUserId(userId.toString());
                    //异步写入MySQL
                    this.cartAsyncService.saveCart(userId.toString(),cart);
                }
                //写入redis
                loginHashOps.put(skuId,JSON.toJSONString(cart));
            });
        }
        //5.删除未登录的购物车
        this.redisTemplate.delete(unLoginKey);
        this.cartAsyncService.deleteCart(userKey);
        //6.返回登录状态的购物车
        List<Object> cartJsons = loginHashOps.values();

        if(!CollectionUtils.isEmpty(cartJsons)){
            return cartJsons.stream().map(cartJson->{
                Cart cart = JSON.parseObject(cartJson.toString(), Cart.class);
                if(this.redisTemplate.hasKey(KEY_PRICE_PREFIX + cart.getSkuId()))
                    cart.setCurrentPrice(new BigDecimal(this.redisTemplate.opsForValue().get(KEY_PRICE_PREFIX + cart.getSkuId())));
                return cart;
            }).collect(Collectors.toList());
        }

        return null;
    }

    /**
     * 购物车修改
     * @param cart
     */
    public void updateNum(Cart cart) {

        String userId = this.getUserId();

        BoundHashOperations<String, Object, Object> hashOps = this.redisTemplate.boundHashOps(KEY_PREFIX + userId);

        String skuId = cart.getSkuId().toString();

        if(!hashOps.hasKey(skuId)){
            throw new CartException("改用户对应的购物车记录不存在");
        }
        BigDecimal count = cart.getCount();
        String cartJson = hashOps.get(skuId).toString();
        cart = JSON.parseObject(cartJson, Cart.class);
        cart.setCount(count);

        hashOps.put(skuId,JSON.toJSONString(cart));
        this.cartAsyncService.updateCartByUserIdAndSkuId(userId,cart);

    }

    /**
     * 修改购物车选种状态
     * @param cart
     */
    public void updateStatus(Cart cart) {
        String userId = this.getUserId();
        BoundHashOperations<String, Object, Object> hashOps = this.redisTemplate.boundHashOps(KEY_PREFIX + userId);
        String skuId = cart.getSkuId().toString();
        if(!hashOps.hasKey(skuId)){
            throw new CartException("改用户对应的购物车记录不存在");
        }
        Boolean check = cart.getCheck();
        String cartJson = hashOps.get(skuId).toString();
        cart = JSON.parseObject(cartJson, Cart.class);
        cart.setCheck(check);

        hashOps.put(skuId,JSON.toJSONString(cart));
        this.cartAsyncService.updateCartByUserIdAndSkuId(userId,cart);
    }

    public void deleteCart(String skuId) {
        String userId = this.getUserId();
        BoundHashOperations<String, Object, Object> hashOps = this.redisTemplate.boundHashOps(KEY_PREFIX + userId);

        if(!hashOps.hasKey(skuId)){
            throw new CartException("改用户对应的购物车记录不存在");
        }

        hashOps.delete(skuId);
        this.cartAsyncService.deleteCartBySkuId(userId,skuId);

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

}
