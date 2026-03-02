package com.hmdp.utils;

import cn.hutool.json.JSONUtil;
import com.hmdp.entity.ChatMsg;
import com.hmdp.service.IChatMsgService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.websocket.*;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket 服务端逻辑处理
 * 路径格式：ws://ip:port/ws/chat/{userId}
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
     * 存当前连接的用户ID
     */
    private Long userId;

    /**
     * 这里不能直接使用 @Autowired 注入 Service
     * 解决方法：通过 Setter 方法注入静态变量
     */
    private static IChatMsgService chatMsgService;

    @Autowired
    public void setChatMsgService(IChatMsgService chatMsgService) {
        WebSocketServer.chatMsgService = chatMsgService;
    }

    /**
     * 连接建立成功调用的方法
     */
    @OnOpen
    public void onOpen(Session session, @PathParam("userId") Long userId) {
        this.userId = userId;
        log.info("WebSocket 连接开启，用户ID：{}", userId);
        // 将连接信息存入 Map
        sessionMap.put(userId, session);
    }

    /**
     * 连接关闭调用的方法
     */
    @OnClose
    public void onClose(@PathParam("userId") Long userId) {
        log.info("WebSocket 连接关闭，用户ID：{}", userId);
        // 从 Map 中移除
        sessionMap.remove(userId);
    }

    /**
     * 收到客户端消息后调用的方法
     * 
     * @param message 消息内容的 JSON 字符串
     */
    @OnMessage
    public void onMessage(String message, Session session) {
        log.info("收到来自用户 {} 的消息：{}", this.userId, message);

        try {
            // 1. 解析消息内容
            // 假设客户端发送的 JSON 格式：{"toUserId": 101, "content": "你好"}
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
}
