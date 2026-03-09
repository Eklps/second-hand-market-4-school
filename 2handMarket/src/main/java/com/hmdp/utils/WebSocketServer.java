package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.json.JSONUtil;
import cn.hutool.jwt.JWT;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.ChatMsg;
import com.hmdp.service.IChatMsgService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.websocket.*;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.hmdp.utils.RedisConstants.JWT_BLACKLIST_KEY;

/**
 * WebSocket 服务端逻辑处理（带 JWT 鉴权，兼容旧路径参数）
 * 新格式：ws://ip:port/ws/chat?token=eyJhbGciOi... （推荐，安全）
 * 旧格式：ws://ip:port/ws/chat/1010 （兼容旧前端）
 * 注意：WebSocketServer 不是单例的，每个连接都会创建一个该类的对象
 */
@ServerEndpoint("/ws/chat/{userId}")
@Component
@Slf4j
public class WebSocketServer {

    /**
     * 静态变量，用来存放所有在线客户端的 Session，key 为 userId
     * 使用 ConcurrentHashMap 保证线程安全
     */
    private static final Map<Long, Session> sessionMap = new ConcurrentHashMap<>();

    /**
     * 存当前连接的用户ID（从 Token 或路径参数中解析得到）
     */
    private Long userId;

    /**
     * 通过 Setter 方法注入静态变量（WebSocket 不支持直接 @Autowired）
     */
    private static IChatMsgService chatMsgService;
    private static StringRedisTemplate stringRedisTemplate;

    @Autowired
    public void setChatMsgService(IChatMsgService chatMsgService) {
        WebSocketServer.chatMsgService = chatMsgService;
    }

    @Autowired
    public void setStringRedisTemplate(StringRedisTemplate stringRedisTemplate) {
        WebSocketServer.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 连接建立时调用
     * 优先从 query 参数 token 做 JWT 鉴权；若无 token 则 fallback 到路径参数 userId（兼容旧前端）
     */
    @OnOpen
    public void onOpen(Session session, @PathParam("userId") Long pathUserId) {
        try {
            // 1. 优先尝试从 query 参数中取 token（新的安全模式）
            String query = session.getQueryString();
            String token = parseTokenFromQuery(query);

            if (token != null && !token.isEmpty()) {
                // 去掉可能的 "Bearer " 前缀
                if (token.startsWith("Bearer ")) {
                    token = token.substring(7);
                }

                // 解析并验证 Token
                JWT jwt = JwtUtils.parseToken(token);
                if (!JwtUtils.verify(jwt)) {
                    log.warn("WebSocket 连接被拒绝：Token 无效或已过期");
                    session.close(new CloseReason(CloseReason.CloseCodes.VIOLATED_POLICY, "Token 无效或已过期"));
                    return;
                }

                // 检查黑名单
                if (stringRedisTemplate != null) {
                    Boolean isBlacklisted = stringRedisTemplate.hasKey(JWT_BLACKLIST_KEY + token);
                    if (Boolean.TRUE.equals(isBlacklisted)) {
                        log.warn("WebSocket 连接被拒绝：Token 已被登出（黑名单）");
                        session.close(new CloseReason(CloseReason.CloseCodes.VIOLATED_POLICY, "Token 已失效"));
                        return;
                    }
                }

                // 提取用户信息
                UserDTO user = BeanUtil.fillBeanWithMap(jwt.getPayloads(), new UserDTO(), false);
                this.userId = user.getId();
                if (this.userId == null) {
                    log.warn("WebSocket 连接被拒绝：Token 中无用户信息");
                    session.close(new CloseReason(CloseReason.CloseCodes.VIOLATED_POLICY, "Token 中无用户信息"));
                    return;
                }
                log.info("WebSocket 连接开启（JWT 鉴权），用户ID：{}", userId);

            } else if (pathUserId != null && pathUserId > 0) {
                // 2. Fallback：使用路径参数中的 userId（兼容旧前端）
                this.userId = pathUserId;
                log.info("WebSocket 连接开启（路径参数兼容模式），用户ID：{}", userId);

            } else {
                log.warn("WebSocket 连接被拒绝：未提供 Token 或 userId");
                session.close(new CloseReason(CloseReason.CloseCodes.VIOLATED_POLICY, "未提供认证信息"));
                return;
            }

            // 鉴权/兼容通过，存入在线用户 Map
            sessionMap.put(userId, session);

        } catch (Exception e) {
            log.error("WebSocket 鉴权异常", e);
            try {
                session.close(new CloseReason(CloseReason.CloseCodes.UNEXPECTED_CONDITION, "鉴权异常"));
            } catch (IOException ignored) {
            }
        }
    }

    /**
     * 连接关闭调用的方法
     */
    @OnClose
    public void onClose() {
        if (this.userId != null) {
            log.info("WebSocket 连接关闭，用户ID：{}", userId);
            sessionMap.remove(userId);
        }
    }

    /**
     * 收到客户端消息后调用的方法
     *
     * @param message 消息内容的 JSON 字符串
     */
    @OnMessage
    public void onMessage(String message, Session session) {
        if (this.userId == null) {
            log.warn("未鉴权的 WebSocket 连接试图发送消息，已忽略");
            return;
        }

        log.info("收到来自用户 {} 的消息：{}", this.userId, message);

        try {
            // 1. 解析消息内容
            // 客户端发送的 JSON 格式：{"toUserId": 101, "content": "你好"}
            ChatMsg chatMsg = JSONUtil.toBean(message, ChatMsg.class);
            chatMsg.setFromUserId(userId);
            chatMsg.setCreateTime(LocalDateTime.now());
            chatMsg.setIsRead(0); // 初始设为未读

            // 2. 消息持久化（保存到数据库）
            chatMsgService.save(chatMsg);

            // 3. 实时推送给目标用户
            Session toSession = sessionMap.get(chatMsg.getToUserId());
            if (toSession != null && toSession.isOpen()) {
                // 如果对方在线，直接通过 WebSocket 发送
                toSession.getBasicRemote().sendText(JSONUtil.toJsonStr(chatMsg));
                log.info("消息已实时推送给目标用户 {}", chatMsg.getToUserId());
            } else {
                log.info("目标用户 {} 不在线，消息已转存为离线消息", chatMsg.getToUserId());
            }
        } catch (Exception e) {
            log.error("处理消息失败", e);
        }
    }

    /**
     * 发生错误时调用
     */
    @OnError
    public void onError(Session session, Throwable error) {
        log.error("WebSocket 发生错误", error);
    }

    /**
     * 服务器主动发送消息的工具方法
     */
    public void sendToUser(Long userId, String message) throws IOException {
        Session session = sessionMap.get(userId);
        if (session != null && session.isOpen()) {
            session.getBasicRemote().sendText(message);
        }
    }

    /**
     * 从 query string 中解析 token 参数
     * 示例输入："token=eyJhbGciOi..." 或 "token=eyJ...&other=val"
     */
    private String parseTokenFromQuery(String queryString) {
        if (queryString == null || queryString.isEmpty()) {
            return null;
        }
        for (String param : queryString.split("&")) {
            String[] kv = param.split("=", 2);
            if (kv.length == 2 && "token".equals(kv[0])) {
                return kv[1];
            }
        }
        return null;
    }
}
