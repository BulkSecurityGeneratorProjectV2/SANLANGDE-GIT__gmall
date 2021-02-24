package com.atguigu.gmall.mms.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "aliyun.sms")
@Data
public class MmsConfig {
    private String regionId;//:
    private String accessKeyId;//:
    private String accessKeySecret;//:
    private String templateCode;//:
    private String signName;//:
}
