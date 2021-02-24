package com.atguigu.gmall.auth;

import com.atguigu.gmall.common.utils.JwtUtils;
import com.atguigu.gmall.common.utils.RsaUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

@SpringBootTest
class GmallAuthApplicationTests {

    private static final String pubKeyPath="E:\\project\\rsa\\rsa.pub";
    private static final String priKeyPath="E:\\project\\rsa\\rsa.pri";

    private PublicKey publicKey;

    private PrivateKey privateKey;

    @Test
    void testGenerateRsa() throws Exception {
        RsaUtils.generateKey(pubKeyPath,priKeyPath,"qwe");
    }

    @BeforeEach
    void testGetRsa() throws Exception {
      this.publicKey=RsaUtils.getPublicKey(pubKeyPath);
      this.privateKey=RsaUtils.getPrivateKey(priKeyPath);
    }

    @Test
    void testGenerateToken() throws Exception {
        Map<String, Object> map =new HashMap<>();
        map.put("id",11);
        map.put("username","zhangsan");
        String token = JwtUtils.generateToken(map, privateKey, 2);
        System.out.println("token = " + token);
    }

    @Test
    void testGetToken() throws Exception {
        String token = "eyJhbGciOiJSUzI1NiJ9.eyJpZCI6MTEsInVzZXJuYW1lIjoiemhhbmdzYW4iLCJleHAiOjE2MTM5ODczNDd9.aN22XKYQ2iabAcJFkJzbDv-520xW_IA34U3JvZWWq-Q3bD3Y3-poa6l2cVrukjVPZPEFpJVerZ0OUppl1DhYUfVD3KYvctzSWmYHFsJBlFpZmxHftcELK-zMC6s2j8KJCkqJ10heCdkJQsNDOhDiFrDhd6zJVZlcG6k0spg_pDFZVvP36u3oTWLdPgvsWD07Q3dWPGoHmVKkl2YiDBVqYYI4ODrrPDOOfg25eInGjeibAlfFAfS10Ozgi6su5-88qRNBsZB4wDkLoCnOVLvvyWYCoKs4cxFYwJqEeyZOehXpYaOYONRVQ0j1zkIJcK5MHgpbW9afMqSr9RGnsDi2cw";
        Map<String, Object> info = JwtUtils.getInfoFromToken(token, publicKey);
        System.out.println("id:"+info.get("id"));
        String username = info.get("username").toString();
        System.out.println("username = " + username);
    }

    @Test
    void contextLoads() {
        Arrays.asList(new String[] {"123","456","789"});
    }

}
