package com.atguigu.gmall.oms.service.impl;

import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.common.bean.ResponseVo;
import com.atguigu.gmall.common.exception.OrderException;
import com.atguigu.gmall.oms.entity.OrderItemEntity;
import com.atguigu.gmall.oms.feign.GmallPmsClient;
import com.atguigu.gmall.oms.feign.GmallSmsClient;
import com.atguigu.gmall.oms.feign.GmallUmsClient;
import com.atguigu.gmall.oms.mapper.OrderItemMapper;
import com.atguigu.gmall.oms.vo.OrderItemVo;
import com.atguigu.gmall.oms.vo.OrderSubmitVo;
import com.atguigu.gmall.pms.entity.*;
import com.atguigu.gmall.ums.entity.UserAddressEntity;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.atguigu.gmall.common.bean.PageResultVo;
import com.atguigu.gmall.common.bean.PageParamVo;

import com.atguigu.gmall.oms.mapper.OrderMapper;
import com.atguigu.gmall.oms.entity.OrderEntity;
import com.atguigu.gmall.oms.service.OrderService;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.Date;
import java.util.List;


@Service("orderService")
public class OrderServiceImpl extends ServiceImpl<OrderMapper, OrderEntity> implements OrderService {

    @Autowired
    private GmallPmsClient pmsClient;

    @Autowired
    private GmallSmsClient smsClient;

    @Autowired
    private OrderItemMapper itemMapper;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Override
    public PageResultVo queryPage(PageParamVo paramVo) {
        IPage<OrderEntity> page = this.page(
                paramVo.getPage(),
                new QueryWrapper<OrderEntity>()
        );

        return new PageResultVo(page);
    }

    @Transactional
    @Override
    public OrderEntity saveOrder(OrderSubmitVo orderSubmitVo, Long userId) {

        List<OrderItemVo> itemVos = orderSubmitVo.getItems();
        if(CollectionUtils.isEmpty(itemVos)){
            throw new OrderException("您购物车没有勾选的商品");
        }
        //新增订单
        OrderEntity orderEntity = new OrderEntity();
        orderEntity.setUserId(userId);
        orderEntity.setOrderSn(orderSubmitVo.getOrderToken());
        orderEntity.setCreateTime(new Date());
        orderEntity.setTotalAmount(orderSubmitVo.getTotalPrice());
        //应付金额：总金额+运费-优惠
        orderEntity.setPayAmount(orderSubmitVo.getTotalPrice().add(orderSubmitVo.getPostFee()));
        orderEntity.setFreightAmount(orderSubmitVo.getPostFee());
        orderEntity.setPayType(orderSubmitVo.getPayType());
        orderEntity.setSourceType(0);
        orderEntity.setStatus(0);
        orderEntity.setDeliveryCompany(orderSubmitVo.getDeliveryCompany());
        orderEntity.setIntegration(orderSubmitVo.getBounds());
        UserAddressEntity address = orderSubmitVo.getAddress();
        if(address!=null){
            orderEntity.setReceiverName(address.getName());
            orderEntity.setReceiverAddress(address.getAddress());
            orderEntity.setReceiverRegion(address.getRegion());
            orderEntity.setReceiverCity(address.getCity());
            orderEntity.setReceiverPhone(address.getPhone());
            orderEntity.setReceiverPostCode(address.getPostCode());
            orderEntity.setReceiverProvince(address.getProvince());
        }
        orderEntity.setDeleteStatus(0);
        orderEntity.setUseIntegration(1000);
        this.save(orderEntity);
        //新增订单详情
        Long orderEntityId = orderEntity.getId();
        itemVos.forEach(item->{
            OrderItemEntity orderItemEntity =new OrderItemEntity();
            orderItemEntity.setOrderId(orderEntityId);
            orderItemEntity.setOrderSn(orderSubmitVo.getOrderToken());
            orderItemEntity.setSkuQuantity(item.getCount().intValue());
            //根据skuId查询sku的修改信息
            ResponseVo<SkuEntity> skuEntityResponseVo = this.pmsClient.querySkuById(item.getSkuId());
            SkuEntity skuEntity = skuEntityResponseVo.getData();
            if (skuEntity != null) {
                orderItemEntity.setSkuId(item.getSkuId());
                orderItemEntity.setSkuName(skuEntity.getName());
                orderItemEntity.setSkuPic(skuEntity.getDefaultImage());
                orderItemEntity.setSkuPrice(skuEntity.getPrice());
                orderItemEntity.setCategoryId(skuEntity.getCategoryId());
            }
            //查询sku销售属性
            ResponseVo<List<SkuAttrValueEntity>> saleAttrsResponseVo = this.pmsClient.querySaleAttrBySkuId(item.getSkuId());
            List<SkuAttrValueEntity> attrValueEntities = saleAttrsResponseVo.getData();
            orderItemEntity.setSkuAttrsVals(JSON.toJSONString(attrValueEntities));
            //查询品牌信息
            ResponseVo<BrandEntity> brandEntityResponseVo = this.pmsClient.queryBrandById(skuEntity.getBrandId());
            BrandEntity brandEntity = brandEntityResponseVo.getData();
            orderItemEntity.setSpuBrand(brandEntity.getName());
            //查询spu相关信息
            ResponseVo<SpuEntity> spuEntityResponseVo = this.pmsClient.querySpuById(skuEntity.getSpuId());
            SpuEntity spuEntity = spuEntityResponseVo.getData();
            if (spuEntity != null) {
                orderItemEntity.setSpuId(spuEntity.getId());
                orderItemEntity.setSpuName(spuEntity.getName());
            }
            //查询spu描述信息
            ResponseVo<SpuDescEntity> spuDescEntityResponseVo = this.pmsClient.querySpuDescById(spuEntity.getId());
            SpuDescEntity spuDescEntity = spuDescEntityResponseVo.getData();
            orderItemEntity.setSpuPic(spuDescEntity.getDecript());

            this.itemMapper.insert(orderItemEntity);
        });
        //定时关单
        this.rabbitTemplate.convertAndSend("ORDER_EXCHANGE","order.close",orderSubmitVo.getOrderToken());
        return orderEntity;
    }

}