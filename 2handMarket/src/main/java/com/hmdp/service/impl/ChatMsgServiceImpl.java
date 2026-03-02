package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.ChatMsg;
import com.hmdp.entity.User;
import com.hmdp.mapper.ChatMsgMapper;
import com.hmdp.service.IChatMsgService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * <p>
 * 用户聊天消息 服务实现类
 * </p>
 */
@Service
public class ChatMsgServiceImpl extends ServiceImpl<ChatMsgMapper, ChatMsg> implements IChatMsgService {

        @Resource
        private IUserService userService;

        @Override
        public Result queryHistory(Long toUserId) {
                Long userId = UserHolder.getUser().getId();
                // 查询双向消息：(from=A and to=B) or (from=B and to=A)
                List<ChatMsg> list = lambdaQuery()
                                .and(q -> q.eq(ChatMsg::getFromUserId, userId).eq(ChatMsg::getToUserId, toUserId))
                                .or(q -> q.eq(ChatMsg::getFromUserId, toUserId).eq(ChatMsg::getToUserId, userId))
                                .orderByAsc(ChatMsg::getCreateTime)
                                .list();
                return Result.ok(list);
        }

        @Override
        public Result queryConversations() {
                Long userId = UserHolder.getUser().getId();
                // 1. 查询该用户的所有消息（作为发送者或接收者）
                List<ChatMsg> msgs = lambdaQuery()
                                .eq(ChatMsg::getFromUserId, userId)
                                .or()
                                .eq(ChatMsg::getToUserId, userId)
                                .orderByDesc(ChatMsg::getCreateTime)
                                .list();

                if (msgs == null || msgs.isEmpty()) {
                        return Result.ok(new ArrayList<>());
                }

                // 2. 按对方 ID 分组，取每组第一条（即最新的一条）
                Map<Long, ChatMsg> lastMsgMap = msgs.stream().collect(Collectors.toMap(
                                m -> m.getFromUserId().equals(userId) ? m.getToUserId() : m.getFromUserId(),
                                m -> m,
                                (existing, replacement) -> existing // 保留最晚的一条
                ));

                // 3. 构建结果列表，包含对方用户信息
                List<Map<String, Object>> result = lastMsgMap.entrySet().stream().map(entry -> {
                        Long otherId = entry.getKey();
                        ChatMsg lastMsg = entry.getValue();
                        User user = userService.getById(otherId);

                        // 构造简单的用户信息
                        UserDTO userDTO = new UserDTO();
                        userDTO.setId(user.getId());
                        userDTO.setNickName(user.getNickName());
                        userDTO.setIcon(user.getIcon());

                        return cn.hutool.core.map.MapUtil.builder("user", (Object) userDTO)
                                        .put("lastMsg", lastMsg.getContent())
                                        .put("time", lastMsg.getCreateTime())
                                        .put("unreadCount",
                                                        lambdaQuery().eq(ChatMsg::getFromUserId, otherId)
                                                                        .eq(ChatMsg::getToUserId, userId)
                                                                        .eq(ChatMsg::getIsRead, 0)
                                                                        .count())
                                        .build();
                }).sorted(
                                (a, b) -> ((java.time.LocalDateTime) b.get("time"))
                                                .compareTo((java.time.LocalDateTime) a.get("time")))
                                .collect(Collectors.toList());

                return Result.ok(result);
        }

        @Override
        public Result markRead(Long fromUserId) {
                Long userId = UserHolder.getUser().getId();
                lambdaUpdate()
                                .set(ChatMsg::getIsRead, 1)
                                .eq(ChatMsg::getFromUserId, fromUserId)
                                .eq(ChatMsg::getToUserId, userId)
                                .eq(ChatMsg::getIsRead, 0)
                                .update();
                return Result.ok();
        }
}
