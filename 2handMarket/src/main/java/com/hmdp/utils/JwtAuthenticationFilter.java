package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import com.hmdp.dto.UserDTO;
import io.jsonwebtoken.Claims;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

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
            Claims claims = JwtUtils.parseToken(token);
            if (JwtUtils.isExpired(claims)) {
                filterChain.doFilter(request, response);
                return;
            }

            // 3. 提取用户信息并存入 SecurityContext (工业级做法)
            // 将 Claims 转回 UserDTO (假设我们存入时做了 Map 转换)
            UserDTO user = BeanUtil.fillBeanWithMap(claims, new UserDTO(), false);

            // 存入 ThreadLocal (兼容原有代码逻辑)
            UserHolder.saveUser(user);

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
