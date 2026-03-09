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
import java.util.*;
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

        @Resource
        private ChatMsgMapper chatMsgMapper;

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

                // ===== 第1次 SQL：查询该用户的所有消息 =====
                List<ChatMsg> msgs = lambdaQuery()
                                .eq(ChatMsg::getFromUserId, userId)
                                .or()
                                .eq(ChatMsg::getToUserId, userId)
                                .orderByDesc(ChatMsg::getCreateTime)
                                .list();

                if (msgs == null || msgs.isEmpty()) {
                        return Result.ok(new ArrayList<>());
                }

                // 按对方 ID 分组，取每组第一条（即最新的一条）
                Map<Long, ChatMsg> lastMsgMap = msgs.stream().collect(Collectors.toMap(
                                m -> m.getFromUserId().equals(userId) ? m.getToUserId() : m.getFromUserId(),
                                m -> m,
                                (existing, replacement) -> existing // 保留最晚的一条
                ));

                // 收集所有联系人 ID
                Set<Long> otherUserIds = lastMsgMap.keySet();
                //这是为了拉出聊天列表

                // ===== 第2次 SQL：批量查询所有联系人信息（替代 N 次 getById）=====
                Map<Long, User> userMap = userService.listByIds(otherUserIds)
                                .stream().collect(Collectors.toMap(User::getId, u -> u));

                // ===== 第3次 SQL：聚合查询所有联系人的未读消息数（替代 N 次 count）=====
                Map<Long, Long> unreadMap = new HashMap<>();
                List<Map<String, Object>> unreadList = chatMsgMapper.countUnreadGroupByFromUser(userId);
                for (Map<String, Object> row : unreadList) {
                        Long fromUserId = ((Number) row.get("from_user_id")).longValue();
                        Long cnt = ((Number) row.get("cnt")).longValue();
                        unreadMap.put(fromUserId, cnt);
                }

                // 构建结果列表
                List<Map<String, Object>> result = otherUserIds.stream().map(otherId -> {
                        ChatMsg lastMsg = lastMsgMap.get(otherId);
                        User user = userMap.get(otherId);

                        UserDTO userDTO = new UserDTO();
                        if (user != null) {
                                userDTO.setId(user.getId());
                                userDTO.setNickName(user.getNickName());
                                userDTO.setIcon(user.getIcon());
                        }

                        return cn.hutool.core.map.MapUtil.builder("user", (Object) userDTO)
                                        .put("lastMsg", lastMsg.getContent())
                                        .put("time", lastMsg.getCreateTime())
                                        .put("unreadCount", unreadMap.getOrDefault(otherId, 0L))
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
