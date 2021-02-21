package com.atguigu.gmall.mms.controller;

import com.atguigu.gmall.mms.service.MmsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
public class MmsController {

    @Autowired
    private MmsService mmsService;

    @Value("${aliyun.sms.signName}")
    private String signName;

    @GetMapping("test")
    public String test(){
        System.out.println("signName = " + signName);
        return signName;
    }

    @PostMapping("send/{phone}")
    public void sendMsg(@PathVariable("phone")String phone){
        this.mmsService.saveMsg(phone);
    }

}
