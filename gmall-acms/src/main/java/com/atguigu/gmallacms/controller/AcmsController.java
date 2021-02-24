package com.atguigu.gmallacms.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("acms")
public class AcmsController {

    @GetMapping("register.html")
    public String register(){
        return "register";
    }

}
