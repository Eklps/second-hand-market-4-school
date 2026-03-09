package com.hmdp.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hmdp.entity.ChatMsg;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * <p>
 * 用户聊天消息 Mapper 接口
 * </p>
 */
public interface ChatMsgMapper extends BaseMapper<ChatMsg> {

    /**
     * 按发送者分组，聚合统计某用户的所有未读消息数
     * 返回 List<Map>: 每条记录包含 from_user_id 和 cnt
     * 一条 SQL 替代 N 次 count 查询，消除 N+1 问题
     */
    @Select("SELECT from_user_id, COUNT(*) AS cnt FROM tb_chat_msg " +
            "WHERE to_user_id = #{toUserId} AND is_read = 0 " +
            "GROUP BY from_user_id")
    List<Map<String, Object>> countUnreadGroupByFromUser(@Param("toUserId") Long toUserId);
}
