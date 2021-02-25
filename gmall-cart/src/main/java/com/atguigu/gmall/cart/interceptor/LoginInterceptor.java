package com.atguigu.gmall.cart.interceptor;

import com.atguigu.gmall.cart.config.JwtProperties;
import com.atguigu.gmall.cart.entity.UserInfo;
import com.atguigu.gmall.common.utils.CookieUtils;
import com.atguigu.gmall.common.utils.JwtUtils;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.UUID;

@Component
@EnableConfigurationProperties(JwtProperties.class)
public class LoginInterceptor implements HandlerInterceptor {

    private static final ThreadLocal<UserInfo> THREAD_LOCAL=new ThreadLocal<>();

    @Autowired
    private JwtProperties jwtProperties;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        //System.out.println("这是拦截器前置方法");

        UserInfo userInfo = new UserInfo();

        String userKey = CookieUtils.getCookieValue(request, this.jwtProperties.getUserKey());
        if(StringUtils.isBlank(userKey)){
            userKey=UUID.randomUUID().toString();
            CookieUtils.setCookie(request,response,jwtProperties.getUserKey(),userKey,jwtProperties.getExpire());
        }
        userInfo.setUserKey(userKey);

        String token = CookieUtils.getCookieValue(request, this.jwtProperties.getCookieName());
        if(StringUtils.isNotBlank(token)){

            try {
                Map<String, Object> map = JwtUtils.getInfoFromToken(token, this.jwtProperties.getPublicKey());
                Long userId = Long.valueOf(map.get("userId").toString());
                userInfo.setUserId(userId);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }


        //状态字段导致线程安全问题，即使加锁也无法解决
        THREAD_LOCAL.set(userInfo);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        //System.out.println("这是拦截器完成时方法");
        //在视图渲染完成之后执行，经常在完成方法中释放资源
        //这里必须清空threadLocal中的资源，因为使用的是tomcat线程池，线程无法结束
        THREAD_LOCAL.remove();
    }

    public static UserInfo getUserInfo(){
        return THREAD_LOCAL.get();
    }
}
