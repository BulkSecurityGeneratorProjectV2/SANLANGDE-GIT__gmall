package com.atguigu.gmall.order.service;

import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.cart.entity.Cart;
import com.atguigu.gmall.common.bean.ResponseVo;
import com.atguigu.gmall.common.bean.UserInfo;
import com.atguigu.gmall.common.exception.OrderException;
import com.atguigu.gmall.oms.entity.OrderEntity;
import com.atguigu.gmall.oms.vo.OrderSubmitVo;
import com.atguigu.gmall.order.feign.*;
import com.atguigu.gmall.order.interceptor.LoginInterceptor;
import com.atguigu.gmall.order.pojo.OrderConfirmVo;
import com.atguigu.gmall.oms.vo.OrderItemVo;
import com.atguigu.gmall.pms.entity.SkuAttrValueEntity;
import com.atguigu.gmall.pms.entity.SkuEntity;
import com.atguigu.gmall.sms.vo.ItemSaleVo;
import com.atguigu.gmall.ums.entity.UserAddressEntity;
import com.atguigu.gmall.ums.entity.UserEntity;
import com.atguigu.gmall.wms.entity.WareSkuEntity;
import com.atguigu.gmall.wms.vo.SkuLockVo;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
public class OrderService {

    @Autowired
    private GmallPmsClient pmsClient;
    @Autowired
    private GmallSmsClient smsClient;
    @Autowired
    private GmallWmsClient wmsClient;
    @Autowired
    private GmallUmsClient umsClient;
    @Autowired
    private GmallCartClient cartClient;
    @Autowired
    private GmallOmsClient omsClient;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private ThreadPoolExecutor threadPoolExecutor;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    private static final String KEY_PREFIX="order:token:";

    public OrderConfirmVo createConfirm() {

        UserInfo userInfo = LoginInterceptor.getUserInfo();
        Long userId = userInfo.getUserId();
        OrderConfirmVo confirmVo = new OrderConfirmVo();
        //1.获取用户ID查询购物车已勾选的商品列表（价格属性实时查询）
        CompletableFuture<List<Cart>> cartsFuture = CompletableFuture.supplyAsync(() -> {
            ResponseVo<List<Cart>> cartResponseVo = this.cartClient.queryCheckedCarts(userId);
            List<Cart> carts = cartResponseVo.getData();
            if (CollectionUtils.isEmpty(carts)) {
                throw new OrderException("没有选中的购物车信息!");
            }
            return carts;
        }, threadPoolExecutor);
        CompletableFuture<Void> orderItemVoFuture = cartsFuture.thenAcceptAsync(carts -> {
            List<OrderItemVo> items = carts.stream().map(cart -> {
                OrderItemVo orderItemVo = new OrderItemVo();
                //只取购物车中的skuId和count，因为其他数据可能和数据库实时数据不同
                orderItemVo.setSkuId(cart.getSkuId());
                orderItemVo.setCount(cart.getCount());
                //根据skuId查询sku信息  V
                CompletableFuture<Void> skuFuture = CompletableFuture.runAsync(() -> {
                    ResponseVo<SkuEntity> skuEntityResponseVo = this.pmsClient.querySkuById(cart.getSkuId());
                    SkuEntity skuEntity = skuEntityResponseVo.getData();
                    orderItemVo.setTitle(skuEntity.getTitle());
                    orderItemVo.setDefaultImage(skuEntity.getDefaultImage());
                    orderItemVo.setPrice(skuEntity.getPrice());
                    orderItemVo.setWeight(skuEntity.getWeight());
                }, threadPoolExecutor);
                //根据skuId查询销售属性   V
                CompletableFuture<Void> saleAttrsFuture = CompletableFuture.runAsync(() -> {
                    ResponseVo<List<SkuAttrValueEntity>> saleAttrsResponseVo = this.pmsClient.querySaleAttrBySkuId(cart.getSkuId());
                    List<SkuAttrValueEntity> attrValueEntities = saleAttrsResponseVo.getData();
                    orderItemVo.setSaleAttrs(attrValueEntities);
                }, threadPoolExecutor);
                //根据skuId查询营销信息   V
                CompletableFuture<Void> salesFuture = CompletableFuture.runAsync(() -> {
                    ResponseVo<List<ItemSaleVo>> salesResponseVo = this.smsClient.querySalesBySkuId(cart.getSkuId());
                    List<ItemSaleVo> itemSaleVos = salesResponseVo.getData();
                    orderItemVo.setSales(itemSaleVos);
                }, threadPoolExecutor);
                //根据skuId查询库存信息   V
                CompletableFuture<Void> wareFuture = CompletableFuture.runAsync(() -> {
                    ResponseVo<List<WareSkuEntity>> wareResponseVo = this.wmsClient.queryWareSkuEntitiesBySkuId(cart.getSkuId());
                    List<WareSkuEntity> wareSkuEntities = wareResponseVo.getData();
                    if (!CollectionUtils.isEmpty(wareSkuEntities))
                        orderItemVo.setStore(wareSkuEntities.stream().anyMatch(wareSkuEntity -> wareSkuEntity.getStock() - wareSkuEntity.getStockLocked() > 0));

                }, threadPoolExecutor);

                CompletableFuture.allOf(skuFuture,saleAttrsFuture,salesFuture,wareFuture).join();

                return orderItemVo;
            }).collect(Collectors.toList());
            confirmVo.setOrderItems(items);
        }, threadPoolExecutor);


        //2.根据用户ID获取收获地址列表
        CompletableFuture<Void> addressesFuture = CompletableFuture.runAsync(() -> {
            ResponseVo<List<UserAddressEntity>> addressesResponseVo = this.umsClient.queryAddressesByUserId(userId);
            List<UserAddressEntity> addressEntities = addressesResponseVo.getData();
            confirmVo.setAddresses(addressEntities);
        }, threadPoolExecutor);

        //3.根据用户ID获取用信息（积分）
        CompletableFuture<Void> userFuture = CompletableFuture.runAsync(() -> {
            ResponseVo<UserEntity> userEntityResponseVo = this.umsClient.queryUserById(userId);
            UserEntity userEntity = userEntityResponseVo.getData();
            confirmVo.setBounds(userEntity.getIntegration());
        }, threadPoolExecutor);
        // 生成唯一表示
        CompletableFuture<Void> orderTokenFuture = CompletableFuture.runAsync(() -> {
            String orderToken = IdWorker.getTimeId();
            //设置orderToken到缓存中
            this.redisTemplate.opsForValue().set(KEY_PREFIX + orderToken, orderToken, 4, TimeUnit.HOURS);
            confirmVo.setOrderToken(orderToken);
        }, threadPoolExecutor);
        CompletableFuture.allOf(orderItemVoFuture,addressesFuture,userFuture,orderTokenFuture).join();

        return confirmVo;
    }

    public void submit(OrderSubmitVo submitVo) {
        //1.防重
        String orderToken = submitVo.getOrderToken();
        if(StringUtils.isBlank(orderToken)){
            throw new OrderException("非法提交");
        }
        String script="if(redis.call('get',KEYS[1])==ARGV[1]) then return redis.call('del',KEYS[1]) else return 0 end";
        Boolean flag = this.redisTemplate.execute(new DefaultRedisScript<>(script, Boolean.class), Arrays.asList(KEY_PREFIX + orderToken), orderToken);
        if(!flag){
            throw new OrderException("请不要重复提交");
        }
        //2.验总价：遍历送货清单，根据skuId获取数据中实时价格 V
        List<OrderItemVo> items = submitVo.getItems();
        if(CollectionUtils.isEmpty(items)){
            throw new OrderException("您还没有选购商品，去添加商品商品到购物车！");
        }
        BigDecimal totalPrice = submitVo.getTotalPrice();
        // 查询实时总价，
        BigDecimal currentTotalPrice = items.stream().map(item -> {
            ResponseVo<SkuEntity> skuEntityResponseVo = this.pmsClient.querySkuById(item.getSkuId());
            SkuEntity skuEntity = skuEntityResponseVo.getData();
            if (skuEntity == null) {
                return new BigDecimal(0);
            }
            return skuEntity.getPrice().multiply(item.getCount());

        }).reduce((a, b) -> a.add(b)).get();
        if(currentTotalPrice.compareTo(totalPrice)!=0){
            throw new OrderException("页面已过期，请刷新后重试");
        }
        //3.验库存并锁定库存
        List<SkuLockVo> lockVos =items.stream().map(item->{
            SkuLockVo lockVo=new SkuLockVo();
            lockVo.setSkuId(item.getSkuId());
            lockVo.setCount(item.getCount().intValue());
            return lockVo;
        }).collect(Collectors.toList());

        ResponseVo<List<SkuLockVo>> lockResponseVo = this.wmsClient.checkAndLock(lockVos, orderToken);
        List<SkuLockVo> skuLockVos = lockResponseVo.getData();
        if(!CollectionUtils.isEmpty(skuLockVos)){
            throw new OrderException(JSON.toJSONString(skuLockVos));
        }
        //此时服务器宕机，需要解锁库存
        //4.下单：oms，新增接口
        UserInfo userInfo = LoginInterceptor.getUserInfo();
        Long userId = userInfo.getUserId();
        OrderEntity orderEntity=new OrderEntity();
        try {
            ResponseVo<OrderEntity> orderEntityResponseVo = this.omsClient.saveOrder(submitVo, userId); //Feign:调用失败或者延迟、响应超时等
            orderEntity = orderEntityResponseVo.getData();
        } catch (Exception e) {
            e.printStackTrace();
            //如果订单创建出现异常，立即解锁库存
            log.error("订单出现异常：{}",e.getMessage());
            this.rabbitTemplate.convertAndSend("ORDER_EXCHANGE","stock.unlock",orderToken);
            throw new OrderException("创建订单时，服务器异常！");
        }

        //5.删除购物车对应的记录(异步删除，即使删除也不会对订单有影响)
        List<Long> skuIds = items.stream().map(OrderItemVo::getSkuId).collect(Collectors.toList());
        Map<String,Object> map = new HashMap<>();
        map.put("userId",userId.toString());
        map.put("skuIds",JSON.toJSONString(skuIds));
        //删除购物车记录
        this.rabbitTemplate.convertAndSend("CART_EXCHANGE","cart.delete",map);
        //return orderEntity;
    }
}
