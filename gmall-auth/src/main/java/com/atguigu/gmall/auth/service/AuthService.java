package com.atguigu.gmall.auth.service;

import com.atguigu.gmall.auth.config.JwtProperties;
import com.atguigu.gmall.auth.feign.GmallUmsClient;
import com.atguigu.gmall.common.bean.ResponseVo;
import com.atguigu.gmall.common.exception.UserException;
import com.atguigu.gmall.common.utils.CookieUtils;
import com.atguigu.gmall.common.utils.IpUtil;
import com.atguigu.gmall.common.utils.JwtUtils;
import com.atguigu.gmall.ums.entity.UserEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.HashMap;
import java.util.Map;

@Service
@EnableConfigurationProperties(JwtProperties.class)
public class AuthService {

    @Autowired
    private GmallUmsClient gmallUmsClient;

    @Autowired
    private JwtProperties jwtProperties;

    public void accredit(String loginName, String password, HttpServletRequest request, HttpServletResponse response) throws Exception {

            //1、远程调用校验用户名密码是否正确
            ResponseVo<UserEntity> userEntityResponseVo = this.gmallUmsClient.query(loginName, password);
            UserEntity userEntity = userEntityResponseVo.getData();

            //2、判断用户信息是否为空
            if(userEntity==null){
                throw new UserException("用户名或密码错误");
            }

            //3、组织载荷
            Map<String, Object> map = new HashMap<>();
            map.put("userId",userEntity.getId());
            map.put("userName",userEntity.getUsername());

            //为了防止jwt被别人盗取，载荷中加入用户ip地址
            String ipAddressAtService = IpUtil.getIpAddressAtService(request);
            map.put("ip",ipAddressAtService);

            //4、生成JWT
            String token = JwtUtils.generateToken(map, this.jwtProperties.getPrivateKey(), this.jwtProperties.getExpire());
            //5、将JWT放入cookie中
            CookieUtils.setCookie(request,response,this.jwtProperties.getCookieName(),token,this.jwtProperties.getExpire() * 60);
            //6、用户昵称放入cookie中，方便页面展示昵称
            CookieUtils.setCookie(request,response,this.jwtProperties.getUnick(),userEntity.getNickname(),this.jwtProperties.getExpire() * 60);

    }
}
