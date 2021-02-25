package com.atguigu.gmall.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

@Configuration
public class CorsConfig {

    @Bean
    public CorsWebFilter corsWebFilter(){
//        初始化CORS对象
        CorsConfiguration configuration=new CorsConfiguration();
        //允许的域名，
        configuration.addAllowedOrigin("http://manager.gmall.com");
        configuration.addAllowedOrigin("http://localhost");
        configuration.addAllowedOrigin("http://127.0.0.1");
        configuration.addAllowedOrigin("http://api.gmall.com");
        configuration.addAllowedOrigin("http://www.gmall.com");
        configuration.addAllowedOrigin("http://item.gmall.com");
        configuration.setAllowCredentials(true);
        //允许的请求的方法
        configuration.addAllowedMethod("*");
        //允许携带的请求头
        configuration.addAllowedHeader("*");

        UrlBasedCorsConfigurationSource configSource = new UrlBasedCorsConfigurationSource();
        configSource.registerCorsConfiguration("/**",configuration);
        return new CorsWebFilter(configSource);
    }

}
