package com.hmdp.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmdp.annotation.AccessLimit;
import com.hmdp.dto.Result;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;

/**
 * 接口限流拦截器 (基于 Redis Lua 脚本，原子操作)
 *
 * 旧版使用 GET + SET/INCREMENT 的非原子操作，并发时存在竞态条件：
 * 多个请求同时 GET 到 null，各自执行 SET("1")，导致计数器被重置、限流失效。
 *
 * 新版通过 Lua 脚本将 INCR + EXPIRE + 阈值判断合并为单次原子操作，
 * 彻底消除并发竞态问题。
 */
@Slf4j
@Component
public class AccessLimitInterceptor implements HandlerInterceptor {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    // 静态初始化 Lua 脚本，避免每次请求都加载
    private static final DefaultRedisScript<Long> ACCESS_LIMIT_SCRIPT;
    static {
        ACCESS_LIMIT_SCRIPT = new DefaultRedisScript<>();
        ACCESS_LIMIT_SCRIPT.setLocation(new ClassPathResource("access_limit.lua"));
        ACCESS_LIMIT_SCRIPT.setResultType(Long.class);
    }

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
            String ip = request.getRemoteAddr();
            key += ":" + ip;
        }

        // 5. 核心限流逻辑：Lua 脚本原子执行 INCR + EXPIRE + 阈值判断
        // 返回 1 表示超过阈值（拒绝），返回 0 表示未超过（放行）
        Long result = stringRedisTemplate.execute(
                ACCESS_LIMIT_SCRIPT,
                Collections.singletonList(key),
                String.valueOf(seconds),
                String.valueOf(maxCount)
        );

        if (result != null && result == 1) {
            log.warn("接口限流触发：URI={}, key={}", request.getRequestURI(), key);
            render(response, Result.fail("您的请求过于频繁，请稍后再试！"));
            return false;
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
