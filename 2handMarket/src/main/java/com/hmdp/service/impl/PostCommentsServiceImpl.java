package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.PostComments;
import com.hmdp.entity.User;
import com.hmdp.mapper.PostCommentsMapper;
import com.hmdp.service.IPostCommentsService;
import com.hmdp.service.IPostService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class PostCommentsServiceImpl extends ServiceImpl<PostCommentsMapper, PostComments>
        implements IPostCommentsService {

    @Resource
    private IUserService userService;

    @Override
    public Result queryCommentsByPostId(Long postId) {
        // 1.查询该帖子的所有评论
        List<PostComments> all = query().eq("post_id", postId).orderByAsc("create_time").list();
        if (all == null || all.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        // 2.构建 id -> comment 映射，并填充用户信息
        Map<Long, PostComments> map = new HashMap<>();
        for (PostComments c : all) {
            User user = userService.getById(c.getUserId());
            if (user != null) {
                c.setNickName(user.getNickName());
                c.setIcon(user.getIcon());
            }
            c.setChildren(new ArrayList<>());
            map.put(c.getId(), c);
        }
        // 3.分离一级评论和回复
        List<PostComments> roots = new ArrayList<>();
        for (PostComments c : all) {
            if (c.getParentId() == null || c.getParentId() == 0L) {
                // 一级评论
                roots.add(c);
            } else {
                // 子回复：挂到一级评论下
                PostComments parent = map.get(c.getParentId());
                if (parent != null) {
                    // 设置被回复者昵称
                    if (c.getAnswerId() != null && c.getAnswerId() != 0L) {
                        PostComments answered = map.get(c.getAnswerId());
                        if (answered != null) {
                            c.setReplyNickName(answered.getNickName());
                        }
                    }
                    parent.getChildren().add(c);
                }
            }
        }
        return Result.ok(roots);
    }

    @Resource
    private IPostService postService;

    @Override
    public Result saveComment(PostComments comment) {
        // 1.获取当前用户（必须登录才能留言）
        comment.setUserId(UserHolder.getUser().getId());
        // 2.一级留言（直接对帖子）时，parentId 和 answerId 默认为 0
        if (comment.getParentId() == null) {
            comment.setParentId(0L);
        }
        if (comment.getAnswerId() == null) {
            comment.setAnswerId(0L);
        }
        // 3.保存评论
        save(comment);
        // 4.修改帖子评论数 +1
        postService.update().setSql("comments = comments + 1").eq("id", comment.getPostId()).update();
        return Result.ok();
    }
}
