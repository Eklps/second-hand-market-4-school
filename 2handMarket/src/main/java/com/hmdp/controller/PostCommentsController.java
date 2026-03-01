package com.hmdp.controller;


import com.hmdp.dto.Result;
import com.hmdp.entity.PostComments;
import com.hmdp.service.IPostCommentsService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/post-comments")
public class PostCommentsController {

    @Resource
    private IPostCommentsService postCommentsService;

    @GetMapping("/{postId}")
    public Result queryCommentsByPostId(@PathVariable("postId") Long postId) {
        return postCommentsService.queryCommentsByPostId(postId);
    }

    @PostMapping
    public Result saveComment(@RequestBody PostComments comment) {
        return postCommentsService.saveComment(comment);
    }

}
