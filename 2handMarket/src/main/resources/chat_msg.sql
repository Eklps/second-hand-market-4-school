-- 聊天消息表
CREATE TABLE `tb_chat_msg` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `from_user_id` bigint(20) NOT NULL COMMENT '发送者ID',
  `to_user_id` bigint(20) NOT NULL COMMENT '接收者ID',
  `content` varchar(2048) NOT NULL COMMENT '消息内容',
  `type` int(11) DEFAULT '0' COMMENT '消息类型：0文字，1图片',
  `is_read` tinyint(1) DEFAULT '0' COMMENT '是否已读：0未读，1已读',
  `create_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_from_user` (`from_user_id`),
  KEY `idx_to_user` (`to_user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户聊天消息表';
