package com.hmdp.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import java.util.Date;
import java.util.Map;

public class JwtUtils {

    // 签名密钥 (实际项目中应放入配置文件)
    private static final String SECRET = "heima-dianping-secret-key-for-jwt-authentication-2026";
    // 过期时间 (默认 7 天)
    private static final long EXPIRE = 1000 * 60 * 60 * 24 * 7;

    /**
     * 生成 Token
     */
    public static String createToken(Map<String, Object> claims) {
        return Jwts.builder()
                .setClaims(claims)
                .setExpiration(new Date(System.currentTimeMillis() + EXPIRE))
                .signWith(SignatureAlgorithm.HS256, SECRET)
                .compact();
    }

    /**
     * 解析 Token
     */
    public static Claims parseToken(String token) {
        return Jwts.parser()
                .setSigningKey(SECRET)
                .parseClaimsJws(token)
                .getBody();
    }

    /**
     * 判断是否过期
     */
    public static boolean isExpired(Claims claims) {
        return claims.getExpiration().before(new Date());
    }
}
