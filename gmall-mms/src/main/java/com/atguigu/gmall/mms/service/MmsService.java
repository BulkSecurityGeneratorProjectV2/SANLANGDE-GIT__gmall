package com.atguigu.gmall.mms.service;

import com.aliyuncs.CommonRequest;
import com.aliyuncs.CommonResponse;
import com.aliyuncs.DefaultAcsClient;
import com.aliyuncs.IAcsClient;
import com.aliyuncs.http.MethodType;
import com.aliyuncs.profile.DefaultProfile;
import com.atguigu.gmall.common.utils.FormUtils;
import com.atguigu.gmall.common.utils.RandomUtils;
import com.atguigu.gmall.mms.config.MmsConfig;
import com.baomidou.mybatisplus.extension.api.R;
import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@EnableConfigurationProperties(MmsConfig.class)
public class MmsService {

    @Autowired
    private MmsConfig mmsConfig;

    private static final String REGISTER_CODE="ums:register:";

    @Autowired
    private StringRedisTemplate redisTemplate;

    public void saveMsg(String phone) {
        boolean isMobile = FormUtils.isMobile(phone);
        //验证手机号是否正确
        if(!isMobile)
            return;
        //生成key:
        String key= REGISTER_CODE +phone;
        //验证是否已经发送过短信
        Object o = redisTemplate.opsForValue().get(key);
        if(!StringUtils.isEmpty(o)) {
            log.error("请勿重复发送！");
        }
        //生成验证码
        String code = RandomUtils.getFourBitRandom();
        //发送验证码
        this.sendMsg(phone,code);
        //set key 乱码：配置key 和 value 序列化  //Service-Base/Redis-config
        redisTemplate.opsForValue().set(key, code, 5, TimeUnit.MINUTES);
    }

    public void sendMsg(String mobile, String code) {
        DefaultProfile profile = DefaultProfile.getProfile(
                mmsConfig.getRegionId(),
                mmsConfig.getAccessKeyId(),
                mmsConfig.getAccessKeySecret());
        IAcsClient client = new DefaultAcsClient(profile);

        CommonRequest request = new CommonRequest();
        request.setSysMethod(MethodType.POST);
        request.setSysDomain("dysmsapi.aliyuncs.com");
        request.setSysVersion("2017-05-25");
        request.setSysAction("SendSms");
        request.putQueryParameter("RegionId", mmsConfig.getRegionId());
        request.putQueryParameter("PhoneNumbers", mobile);
        request.putQueryParameter("SignName", mmsConfig.getSignName());
        request.putQueryParameter("TemplateCode", mmsConfig.getTemplateCode());

        Map<String, String> map = new HashMap<>();
        map.put("code",code);
        //将包含验证码的集合转换为json字符串
        Gson gson = new Gson();
        request.putQueryParameter("TemplateParam",gson.toJson(map));
        try {
            CommonResponse response = client.getCommonResponse(request);

            String data = response.getData();
            Map<String,String> params = gson.fromJson(data, Map.class);
            String success = params.get("Code");
            String message = map.get("Message");
            //配置参考：短信服务->系统设置->国内消息设置
            //错误码参考：
            //https://help.aliyun.com/document_detail/101346.html?spm=a2c4g.11186623.6.613.3f6e2246sDg6Ry
            //控制所有短信流向限制（同一手机号：一分钟一条、一个小时五条、一天十条）
            if ("isv.BUSINESS_LIMIT_CONTROL".equals(success)) {
                log.error("短信发送过于频繁 " + "【code】" + success + ", 【message】" + message);
            }

            if (!"OK".equals(success)) {
                log.error("短信发送失败 " + " - code: " + success + ", message: " + message);
            }
        } catch (Exception e) {
            log.error(ExceptionUtils.getStackTrace(e));
        }
    }

}
