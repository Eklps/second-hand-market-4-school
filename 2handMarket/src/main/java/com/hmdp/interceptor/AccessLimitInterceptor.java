package com.hmdp.interceptor;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmdp.annotation.AccessLimit;
import com.hmdp.dto.Result;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * 接口限流拦截器 (基于 Redis)
 */
@Component
public class AccessLimitInterceptor implements HandlerInterceptor {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        // 1. 判断拦截的请求是否是 Controller 的方法请求
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }

        HandlerMethod hm = (HandlerMethod) handler;
        // 2. 尝试获取该方法上的 @AccessLimit 注解
        AccessLimit accessLimit = hm.getMethodAnnotation(AccessLimit.class);

        System.out.println("【限流拦截器】URI=" + request.getRequestURI() + "，是否拦截到注解=" + (accessLimit != null));

        // 如果没有加上该注解，说明该接口不需要限流，直接放行
        if (accessLimit == null) {
            return true;
        }

        // 3. 获取注解上的配置参数
        int seconds = accessLimit.seconds();
        int maxCount = accessLimit.maxCount();
        boolean needLogin = accessLimit.needLogin();

        // 4. 构建 Redis 的 Key (考虑到不同的用户限流应当独立)
        String key = "access:limit:" + request.getRequestURI();

        if (needLogin) {
            // 需要登录的接口，以用户 ID 为维度进行限流
            if (UserHolder.getUser() == null) {
                render(response, Result.fail("请先登录"));
                return false;
            }
            key += ":" + UserHolder.getUser().getId();
        } else {
            // 不需要登录的公开接口，以 IP 地址为维度进行限流防刷
            // 注意：真实生产环境中可能需要读取 X-Forwarded-For 等 Header
            String ip = request.getRemoteAddr();
            key += ":" + ip;
        }

        // 5. 核心限流逻辑：Lua 脚本更好，这里用通俗的 Java 逻辑实现
        String countStr = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isBlank(countStr)) {
            // 第一次访问
            stringRedisTemplate.opsForValue().set(key, "1", seconds, TimeUnit.SECONDS);
        } else {
            int count = Integer.parseInt(countStr);
            if (count < maxCount) {
                // 没超阈值，自增
                stringRedisTemplate.opsForValue().increment(key);
            } else {
                // 超过阈值，直接拦截并返回错误信息
                render(response, Result.fail("您的请求过于频繁，请稍后再试！"));
                return false;
            }
        }

        return true;
    }

    /**
     * 将 Result 对象转为 JSON 并写入 HttpServletResponse 回给前端的辅助方法
     */
    private void render(HttpServletResponse response, Result result) throws IOException {
        response.setContentType("application/json;charset=UTF-8");
        ObjectMapper mapper = new ObjectMapper();
        response.getWriter().write(mapper.writeValueAsString(result));
    }
}
