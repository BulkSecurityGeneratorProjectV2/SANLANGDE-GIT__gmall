package com.atguigu.gmall.mms.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "aliyun.sms")
@Data
public class MmsConfig {
    private String regionId;//:
    private String accessKeyId;//:
    private String accessKeySecret;//:
    private String templateCode;//:
    private String signName;//:
}
