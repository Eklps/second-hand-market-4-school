package com.hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.dto.Result;
import com.hmdp.entity.ChatMsg;

import java.util.List;

/**
 * <p>
 * 用户聊天消息 服务类
 * </p>
 */
public interface IChatMsgService extends IService<ChatMsg> {

    /**
     * 查询与某人的历史聊天记录
     */
    Result queryHistory(Long toUserId);

    /**
     * 查询对话列表（最近联系人）
     */
    Result queryConversations();

    /**
     * 标记某人的消息为已读
     */
    Result markRead(Long fromUserId);
}
