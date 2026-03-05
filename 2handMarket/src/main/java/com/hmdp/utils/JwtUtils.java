package com.hmdp.utils;

import cn.hutool.core.date.DateField;
import cn.hutool.core.date.DateTime;
import cn.hutool.jwt.JWT;
import cn.hutool.jwt.JWTUtil;

import java.util.Map;

public class JwtUtils {

    // 签名密钥 (实际项目中应放入配置文件)
    private static final byte[] SECRET = "heima-dianping-secret-key-for-jwt-authentication-2026".getBytes();
    // 过期时间 (默认 7 天)
    private static final int EXPIRE = 1000 * 60 * 60 * 24 * 7;

    /**
     * 生成 Token
     */
    public static String createToken(Map<String, Object> claims) {
        DateTime now = DateTime.now();
        DateTime expireTime = now.offsetNew(DateField.MILLISECOND, EXPIRE);

        return JWT.create()
                .addPayloads(claims)
                .setExpiresAt(expireTime)
                .setKey(SECRET)
                .sign();
    }

    /**
     * 解析 Token
     */
    public static JWT parseToken(String token) {
        return JWTUtil.parseToken(token);
    }

    /**
     * 判断签名是否有效且未过期
     */
    public static boolean verify(JWT jwt) {
        // verify 验证签名，validate 验证是否过期 (默认允许0秒误差)
        return jwt.setKey(SECRET).verify() && jwt.validate(0);
    }
}
