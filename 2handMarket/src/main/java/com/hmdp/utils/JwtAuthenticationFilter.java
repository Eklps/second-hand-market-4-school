package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.jwt.JWT;
import com.hmdp.dto.UserDTO;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.annotation.Resource;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import cn.hutool.core.bean.copier.CopyOptions;

import static com.hmdp.utils.RedisConstants.JWT_BLACKLIST_KEY;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // 1. 获取 Header 中的 Token
        String token = request.getHeader("authorization");
        if (token == null || !token.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        token = token.substring(7); // 去掉 "Bearer " 前缀

        try {
            // 2. 解析 Token
            JWT jwt = JwtUtils.parseToken(token);
            if (!JwtUtils.verify(jwt)) {
                filterChain.doFilter(request, response);
                return;
            }

            // 2.5 检查黑名单：该 Token 是否已被登出
            Boolean isBlacklisted = stringRedisTemplate.hasKey(JWT_BLACKLIST_KEY + token);
            if (Boolean.TRUE.equals(isBlacklisted)) {
                filterChain.doFilter(request, response);
                return;
            }

            // 3. 提取用户信息并存入 SecurityContext (工业级做法)
            // 将 Payload 转回 UserDTO
            UserDTO user = BeanUtil.fillBeanWithMap(jwt.getPayloads(), new UserDTO(), false);

            // 存入 ThreadLocal (兼容原有代码逻辑)
            UserHolder.saveUser(user);

            // 4. Token 刷新机制 (滑动过期)
            // 检查剩余失效时间，如果不足 3 天，则返回一个新的 Token
            Object expObj = jwt.getPayload("exp");
            if (expObj != null) {
                long expireMs = Long.parseLong(expObj.toString()) * 1000;
                long remainMs = expireMs - System.currentTimeMillis();
                long thresholdMs = 1000 * 60 * 60 * 24 * 3L; // 3 天
                if (remainMs > 0 && remainMs < thresholdMs) {
                    Map<String, Object> userMap = BeanUtil.beanToMap(user, new HashMap<>(),
                            CopyOptions.create()
                                    .setIgnoreNullValue(true)
                                    .setFieldValueEditor((k, v) -> v != null ? v.toString() : null));
                    String newToken = JwtUtils.createToken(userMap);
                    // 将新 Token 写入 Response Header，允许跨域访问
                    response.setHeader("Access-Control-Expose-Headers", "Refresh-Token");
                    response.setHeader("Refresh-Token", newToken);
                }
            }

            // 存入 Security 上下文
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(user, null,
                    Collections.emptyList());
            SecurityContextHolder.getContext().setAuthentication(authentication);

        } catch (Exception e) {
            // Token 异常一律不处理，交给后续 LoginInterceptor 拦截（如果有的话）或者 Security 的规则
        }

        filterChain.doFilter(request, response);
    }
}
