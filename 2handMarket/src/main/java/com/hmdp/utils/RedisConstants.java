package com.hmdp.utils;

public class RedisConstants {
    public static final String LOGIN_CODE_KEY = "login:code:";
    public static final Long LOGIN_CODE_TTL = 2L;
    public static final String LOGIN_USER_KEY = "login:token:";
    public static final Long LOGIN_USER_TTL = 36000L;

    public static final Long CACHE_NULL_TTL = 2L;
    public static final String CACHE_SHOP_TYPE_KEY = "cache:product:key";
    public static final Long CACHE_SHOP_TYPE_KEY_TTL = 24L;
    public static final Long CACHE_SHOP_TTL = 30L;
    public static final String CACHE_SHOP_KEY = "cache:product:";

    public static final String LOCK_SHOP_KEY = "lock:product:";
    public static final Long LOCK_SHOP_TTL = 10L;

    // 用于优化中，存储订单到redis中
    public static final String SECKILL_STOCK_KEY = "seckill:stock:";
    public static final String BLOG_LIKED_KEY = "post:liked:";
    public static final String FEED_KEY = "feed:";
    public static final String SHOP_GEO_KEY = "product:geo:";
    public static final String CACHE_POST_KEY = "cache:post:";
    public static final Long CACHE_POST_TTL = 30L;
    public static final String LOCK_POST_KEY = "lock:post:";
    public static final String USER_SIGN_KEY = "sign:";
    public static final String POST_GEO_KEY = "post:geo:";
    public static final String JWT_BLACKLIST_KEY = "jwt:blacklist:";
}
