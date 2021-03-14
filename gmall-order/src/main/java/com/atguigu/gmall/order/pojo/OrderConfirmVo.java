package com.atguigu.gmall.order.pojo;

import com.atguigu.gmall.oms.vo.OrderItemVo;
import com.atguigu.gmall.ums.entity.UserAddressEntity;
import lombok.Data;

import java.util.List;

@Data
public class OrderConfirmVo {

    // 查询用户的所有收获地址
    private List<UserAddressEntity> addresses;

    private List<OrderItemVo> orderItems;

    private Integer bounds; //用户的购物积分

    private String orderToken;  //订单confirm页面隐藏参数，防止订单重复提交、恶意刷单
}
