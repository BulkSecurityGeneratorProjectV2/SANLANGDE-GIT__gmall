package com.atguigu.gmall.payment.controller;

import com.alibaba.fastjson.JSON;
import com.alipay.api.AlipayApiException;
import com.atguigu.gmall.common.bean.ResponseVo;
import com.atguigu.gmall.common.bean.UserInfo;
import com.atguigu.gmall.common.exception.OrderException;
import com.atguigu.gmall.oms.entity.OrderEntity;
import com.atguigu.gmall.payment.config.AlipayTemplate;
import com.atguigu.gmall.payment.entity.PaymentInfoEntity;
import com.atguigu.gmall.payment.interceptor.LoginInterceptor;
import com.atguigu.gmall.payment.service.PaymentService;
import com.atguigu.gmall.payment.vo.PayAsyncVo;
import com.atguigu.gmall.payment.vo.PayVo;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import org.apache.commons.lang3.StringUtils;
import org.redisson.api.RCountDownLatch;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;

@Controller
public class PaymentController {

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private AlipayTemplate alipayTemplate;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private RedissonClient redissonClient;

    @GetMapping("pay.html")
    public String toPay(@RequestParam("orderToken") String orderToken, Model model){
        UserInfo userInfo = LoginInterceptor.getUserInfo();
        Long userId = userInfo.getUserId();
        OrderEntity orderEntity = this.paymentService.queryOrderByToken(orderToken);
        if(orderEntity==null){
            throw new OrderException("订单提交异常！请返回重试");
        }
        //判断订单是否属于该用户
        if(userId != orderEntity.getUserId()){
            throw new OrderException("该订单不属于你！或者没有权限");
        }
        if(orderEntity.getStatus()!=0){
            throw new OrderException("该订单状态异常！请刷新");
        }
        model.addAttribute("orderEntity",orderEntity);
        return "pay";
    }

    @GetMapping("alipay.html")
    @ResponseBody
    public String paySuccess(@RequestParam("orderToken") String orderToken){
        UserInfo userInfo = LoginInterceptor.getUserInfo();
        Long userId = userInfo.getUserId();

        OrderEntity orderEntity = this.paymentService.queryOrderByToken(orderToken);
        if(orderEntity==null){
            throw new OrderException("订单提交异常！请返回重试");
        }
        //判断订单是否属于该用户
        if(userId != orderEntity.getUserId()){
            throw new OrderException("该订单不属于你！或者没有权限");
        }
        if(orderEntity.getStatus()!=0){
            throw new OrderException("该订单状态异常！请刷新");
        }

        try {
            PayVo payVo = new PayVo();
            payVo.setOut_trade_no(orderToken);
            payVo.setTotal_amount("0.01");
            payVo.setSubject("谷粒商城商品支付");
            //添加支付对账信息表
            String payId = this.paymentService.savePaymentInfo(orderEntity);
            //回传参数。公共回传参数，如果请求时传递了该参数，则返回给商户时会在异步通知时将该参数原样返回。
            payVo.setPassback_params(payId);   //
            return this.alipayTemplate.pay(payVo);
        } catch (AlipayApiException e) {
            e.printStackTrace();
            throw new OrderException("订单提交异常，请刷新！");
        }
    }

    @GetMapping("/pay/success")
    public Object paySuccess(PayAsyncVo payAsyncVo, Model model){
        String total_amount = payAsyncVo.getTotal_amount();

        model.addAttribute("total_amount",total_amount);
        return "paysuccess";
    }

    @PostMapping("/pay/ok")
    @ResponseBody
    public String payOk(PayAsyncVo payAsyncVo){
        System.out.println("payAsyncVo = " + payAsyncVo);
        /**payAsyncVo = PayAsyncVo(
         * gmt_create=2021-03-01 20:55:41,
         * charset=utf-8,
         * gmt_payment=2021-03-01 20:55:49,
         * notify_time=2021-03-01 20:55:50,
         * subject=谷粒商城商品支付,
         * sign=Wbx14ghQFUF2a/yn9yf70qSnABqU+Cemy8k1DO/j3oKhIKxyNGt41Pq+zkS/mQg8lxsbETGc/9yJ/IujQuPzijg+16JR4a6or1SpxffK7O4QQfViYdL1ZgBIsHfgGjuK7DNb4txxInymhom8DKKF7EVqZVdHt525fwjvb9FHqUcfYUWShm7iliZACOwRCIC0f3BgKCXYTeKvQzOIBkuTlN1Hp8O9U5r+X8psN+yHRtFAo8yy0DRkAKui1Y1ilVGmyPn4v3ytGQtfeISFJWNykTtxtMaMsj3TifiPIWtfaHnekd79A3Y39AJZEGNIglieJ5B6/mVlaBNWGnLO3vs2SQ==,
         * buyer_id=2088122625573955, body=null, invoice_amount=0.01, version=1.0, notify_id=2021030100222205549073951434717422,
         * fund_bill_list=[{"amount":"0.01","fundChannel":"PCREDIT"}], notify_type=trade_status_sync, out_trade_no=202103012055152781366371454110670850,
         * total_amount=0.01, trade_status=TRADE_SUCCESS, trade_no=2021030122001473951411498543, auth_app_id=2021001163617452, receipt_amount=0.01,
         * point_amount=0.00, app_id=2021001163617452, buyer_pay_amount=0.01, sign_type=RSA2, seller_id=2088831489324244)
         */
        //1. 验签
        Boolean signature = this.alipayTemplate.checkSignature(payAsyncVo);
        if(!signature){
            return "failure";
        }
        //2.校验业务参数:app_id,out_trade_no,total_amount
        String app_id = payAsyncVo.getApp_id();
        String out_trade_no = payAsyncVo.getOut_trade_no();
        String total_amount = payAsyncVo.getTotal_amount();
        String payId = payAsyncVo.getPassback_params();
        PaymentInfoEntity paymentInfoEntity =this.paymentService.queryPaymentInfo(payId);
        if(!StringUtils.equals(app_id,alipayTemplate.getApp_id())
                || !StringUtils.equals(out_trade_no,paymentInfoEntity.getOutTradeNo())
                || new BigDecimal(total_amount).compareTo(paymentInfoEntity.getTotalAmount())!=0){
            return "failure";
        }
        //3.校验支付状态
        String trade_status = payAsyncVo.getTrade_status();
        if(!StringUtils.equals("TRADE_SUCCESS",trade_status)){
            return "failure";
        }
        //4.更新队长对账信息
        if (this.paymentService.updatePaymentInfo(payAsyncVo,payId)==0) {
            return "failure";
        }
        //5.异步 发送消息给订单更新状态（且更新库存）
        this.rabbitTemplate.convertAndSend("ORDER_EXCHANGE","order.success",out_trade_no);
        //6.响应成功
        return "success";
    }

    @GetMapping("secKill/{skuId}")
    public ResponseVo<Object> secKill(@PathVariable("skuId") Long skuId){
        RLock fairLock = this.redissonClient.getFairLock("seckill:lock:" + skuId);

        fairLock.lock();
        String stock = this.redisTemplate.opsForValue().get("seckill:lock:" + skuId);
        if(StringUtils.isBlank(stock) || Integer.parseInt(stock)==0)
            throw new OrderException("秒杀不存在或者秒杀已结束！");
        //减库存
        this.redisTemplate.opsForValue().decrement("seckill:lock:" + skuId);

        HashMap<Object, Object> hashMap = new HashMap<>();
        hashMap.put("skuId",skuId);
        hashMap.put("count",1);
        UserInfo userInfo = LoginInterceptor.getUserInfo();
        hashMap.put("userId",userInfo.getUserId());
        String orderToken = IdWorker.getTimeId();
        hashMap.put("orderToken",orderToken);
        //异步创建订单，并减库存
        this.rabbitTemplate.convertAndSend("ORDER_EXCHANGE","seckill.success", JSON.toJSONString(hashMap));
        //使用闭锁：订单号
        RCountDownLatch countDownLatch = this.redissonClient.getCountDownLatch("seckill:countdown:" + orderToken);
        countDownLatch.trySetCount(1l);

        fairLock.unlock();
        return ResponseVo.ok("秒杀成功！");
    }

    @GetMapping("order/{orderToken}")
    public ResponseVo<OrderEntity> queryOrderByOrderToken(@PathVariable("orderToken")String orderToken){
        RCountDownLatch countDownLatch = this.redissonClient.getCountDownLatch("seckill:countdown:" + orderToken);
        try {
            countDownLatch.await();
            UserInfo userInfo = LoginInterceptor.getUserInfo();
            OrderEntity orderEntity = this.paymentService.queryOrderByToken(orderToken);
            if(userInfo.getUserId()==orderEntity.getUserId())
                return ResponseVo.ok();
        } catch (InterruptedException e) {
            e.printStackTrace();
            throw new OrderException("订单创建中，请稍后再试！");
        }
        return null;
    }

}
