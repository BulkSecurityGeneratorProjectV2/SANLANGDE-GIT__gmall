package com.atguigu.gmall.mms.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "aliyun.sms")
@Data
public class MmsConfig {
    private String regionId;//: cn-hangzhou
    private String accessKeyId;//: LTAI4GJyYdac9BqWv9HEkbgj
    private String accessKeySecret;//: e8ZZNpTmwqzd6nZgU7gYxdGrOLCNxJ
    private String templateCode;//: SMS_207963792
    private String signName;//: 美年旅游
}
