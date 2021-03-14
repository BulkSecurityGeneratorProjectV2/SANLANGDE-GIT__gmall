package com.atguigu.gmall.oms.vo;

import com.atguigu.gmall.ums.entity.UserAddressEntity;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class OrderSubmitVo {

    private String orderToken;//订单编号
    private UserAddressEntity address; //收获地址
    private Integer payType; //支付类型
    private String deliveryCompany;//快递配送方式
    private Integer bounds; //积分信息
    private List<OrderItemVo> items;    //送货清单
    private BigDecimal totalPrice;  //总金额
    private BigDecimal postFee; //运费

}
