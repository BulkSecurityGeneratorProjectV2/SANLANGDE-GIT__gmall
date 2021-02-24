package com.atguigu.gmall.cart.controller;

import com.atguigu.gmall.cart.entity.Cart;
import com.atguigu.gmall.cart.service.CartService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.concurrent.ListenableFuture;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;


@Controller
public class CartController {

    @Autowired
    private CartService cartService;

    @GetMapping("cart.html")
    public String queryCarts(Model model){
        List<Cart> carts = cartService.queryCarts();
        model.addAttribute("carts",carts);
        return "cart";
    }

    @GetMapping
    public String saveCart(Cart cart){
        this.cartService.saveCart(cart);
        return "redirect:http://cart.gmall.com/addCart.html?skuId=" + cart.getSkuId();
    }

    @GetMapping("addCart.html")
    public String toCart(@RequestParam("skuId")Long skuId, Model model){
        Cart cart = this.cartService.queryCartBySkuId(skuId);
        model.addAttribute("cart",cart);
        return "addCart";
    }

    @RequestMapping("test")
    @ResponseBody
    public String test(){
        //System.out.println(LoginInterceptor.getUserInfo());
        long start = System.currentTimeMillis();

        this.cartService.executor1();
        this.cartService.executor2();

//
//        executor1.addCallback(result->{
//            System.out.println("result = " + result);
//        },throwable -> {
//            System.out.println(throwable.getMessage());
//        });
//
//        executor2.addCallback(result->{
//            System.out.println("result = " + result);
//        },throwable -> {
//            System.out.println(throwable.getMessage());
//        });

        System.out.println("执行时间" + (System.currentTimeMillis()-start));
        return "Hello cart";
    }


}
