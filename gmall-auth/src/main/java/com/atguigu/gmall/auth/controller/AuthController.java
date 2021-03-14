package com.atguigu.gmall.auth.controller;

import com.atguigu.gmall.auth.service.AuthService;
import com.atguigu.gmall.common.exception.AuthException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Controller
public class AuthController {

    @Autowired
    private AuthService authService;

    @GetMapping("toLogin.html")
    public String toLogin(@RequestParam(value = "returnUrl",defaultValue = "http://www.gmall.com")String returnUrl, Model model){
        // 把登录前的页面地址，记录到登录页面，以备将来登录成功，回到登录前的页面
        model.addAttribute("returnUrl",returnUrl);
        return "login";
    }

    @PostMapping("login")
    public String login(@RequestParam("loginName")String loginName,
                        @RequestParam("password")String password,
                        @RequestParam("returnUrl")String returnUrl,
                        HttpServletRequest request,
                        HttpServletResponse response){
        try {
            this.authService.accredit(loginName,password,request,response);
        } catch (Exception e) {
            throw new AuthException("登录失败，请重试");
        }
        return "redirect:"+returnUrl;
    }

}
