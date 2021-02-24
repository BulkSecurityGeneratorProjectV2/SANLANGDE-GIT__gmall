package com.atguigu.gmall.auth.test;

import com.atguigu.gmall.common.utils.JwtUtils;
import com.atguigu.gmall.common.utils.RsaUtils;
import org.junit.Test;
import org.junit.jupiter.api.BeforeEach;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.HashMap;
import java.util.Map;

public class JwtTest {

    // 别忘了创建D:\\project\rsa目录
	private static final String pubKeyPath = "E:\\project\\rsa\\rsa.pub";
    private static final String priKeyPath = "E:\\project\\rsa\\rsa.pri";

    private PublicKey publicKey;

    private PrivateKey privateKey;

    @Test
    public void testRsa() throws Exception {
        RsaUtils.generateKey(pubKeyPath, priKeyPath, "234");
    }

    @BeforeEach
    public void testGetRsa() throws Exception {
        this.publicKey = RsaUtils.getPublicKey(pubKeyPath);
        this.privateKey = RsaUtils.getPrivateKey(priKeyPath);
    }

    @Test
    public void testGenerateToken() throws Exception {
        Map<String, Object> map = new HashMap<>();
        map.put("id", "11");
        map.put("username", "liuyan");
        // 生成token
        String token = JwtUtils.generateToken(map, privateKey, 5);
        System.out.println("token = " + token);
    }

    @Test
    public void testParseToken() throws Exception {
        String token = "eyJhbGciOiJSUzI1NiJ9.eyJpZCI6IjExIiwidXNlcm5hbWUiOiJsaXV5YW4iLCJleHAiOjE1ODYwOTg0MDd9.Gjt968x1OhFVUSDvnKK_TdNgau6wFCLXF98Teosidf__FewtOW3ytA5I1H9jU3DVzrhDfZl0fFfxNJrPPb75_WNKj06f6lB2yRy8fbazzVDrtzsBcPqEa1HeVoNA3NmUVQNlPC3ckYhZ-yu9BT3km3lY0eGum_jPivBHLsXLMbFnSnpXIjYi3kguJfXXRZYKuanGttCV6t7uCWd10GWhEBbXhIi81houaALr2cDWtqHUBC6FbJ0oVdxAaZixwnZJm_vSUjmYjM062H3CJwX44WCxLZXhSRCWhWo3HGpSU2LuUyfd_IJw8MDdI5w31P3dRczAjMjMykAhGBlOCGwy7Q";

        // 解析token
        Map<String, Object> map = JwtUtils.getInfoFromToken(token, publicKey);
        System.out.println("id: " + map.get("id"));
        System.out.println("userName: " + map.get("username"));
    }
}