package com.hmdp.controller;

import com.hmdp.dto.Result;
import com.hmdp.service.IChatMsgService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * 聊天功能控制器
 */
@RestController
@RequestMapping("/chat")
public class ChatController {

    @Resource
    private IChatMsgService chatMsgService;

    /**
     * 获取与某人的历史记录
     */
    @GetMapping("/history/{toUserId}")
    public Result queryHistory(@PathVariable("toUserId") Long toUserId) {
        return chatMsgService.queryHistory(toUserId);
    }

    /**
     * 获取对话列表（最近联系人）
     */
    @GetMapping("/list")
    public Result queryConversations() {
        return chatMsgService.queryConversations();
    }

    /**
     * 标记消息为已读
     */
    @PutMapping("/read/{fromUserId}")
    public Result markRead(@PathVariable("fromUserId") Long fromUserId) {
        return chatMsgService.markRead(fromUserId);
    }
}
