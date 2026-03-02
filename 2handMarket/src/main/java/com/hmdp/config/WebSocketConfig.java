package com.hmdp.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.server.standard.ServerEndpointExporter;

/**
 * WebSocket 配置类
 * 开启 WebSocket 支持
 */
@Configuration
public class WebSocketConfig {

    /**
     * 注入 ServerEndpointExporter
     * 该 Bean 会自动注册使用了 @ServerEndpoint 注解声明的 WebSocket endpoint
     * 注意：如果是在外部 Tomcat 中运行，不需要此 Bean，SpringBoot 会自动处理。
     */
    @Bean
    public ServerEndpointExporter serverEndpointExporter() {
        return new ServerEndpointExporter();
    }
}
