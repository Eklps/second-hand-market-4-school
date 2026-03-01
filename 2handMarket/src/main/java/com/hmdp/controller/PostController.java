package com.hmdp.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Post;
import com.hmdp.service.IPostService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/post")
public class PostController {

    @Resource
    private IPostService postService;
    @Resource
    private IUserService userService;

    /**
     * 根据id查询博客
     */
    @GetMapping("/{id}")
    public Result queryPostById(
            @PathVariable("id") Long id,
            @RequestParam(value = "x", required = false) Double x,
            @RequestParam(value = "y", required = false) Double y) {
        return postService.queryPostById(id, x, y);
    }

    @PostMapping
    public Result savePost(@RequestBody Post post) {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        post.setUserId(user.getId());
        // 保存探店博文
        postService.save(post);
        // 返回id
        return Result.ok(post.getId());
    }

    @PutMapping("/like/{id}")
    public Result likePost(@PathVariable("id") Long id) {
        return postService.likePost(id);
    }

    @GetMapping("/of/me")
    public Result queryMyPost(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        // 根据用户查询
        Page<Post> page = postService.query()
                .eq("user_id", user.getId()).page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Post> records = page.getRecords();
        return Result.ok(records);
    }

    @GetMapping("/hot")
    public Result queryHotPost(
            @RequestParam(value = "current", defaultValue = "1") Integer current,
            @RequestParam(value = "x", required = false) Double x,
            @RequestParam(value = "y", required = false) Double y,
            @RequestParam(value = "campus", required = false) String campus) {
        return postService.queryHotPost(current, x, y, campus);
    }

    @GetMapping("/likes/{id}")
    public Result queryPostLikes(@PathVariable("id") Long id) {
        return postService.queryPostLikes(id);
    }

    @GetMapping("/of/follow")
    public Result queryPostOfFollow(
            @RequestParam("lastId") Long max,
            @RequestParam(value = "offset", defaultValue = "0") Integer offset) {
        return postService.queryPostOfFollow(max, offset);
    }

    @GetMapping("/of/product/{id}")
    public Result queryPostByProductId(
            @PathVariable("id") Long id,
            @RequestParam(value = "current", defaultValue = "1") Integer current) {
        return postService.queryPostByProductId(id, current);
    }

    @GetMapping("/of/category/{id}")
    public Result queryPostByCategoryId(
            @PathVariable("id") Long id,
            @RequestParam(value = "current", defaultValue = "1") Integer current,
            @RequestParam(value = "campus", required = false) String campus) {
        return postService.queryPostByCategoryId(id, current, campus);
    }

}
