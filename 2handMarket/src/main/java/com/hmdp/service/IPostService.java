package com.hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.dto.Result;
import com.hmdp.entity.Post;

/**
 * <p>
 * 服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IPostService extends IService<Post> {
    /**
     * 查询热门笔记
     */
    Result queryHotPost(Integer current, Double x, Double y, String campus);

    /**
     * 根据ID查询笔记详情
     */
    Result queryPostById(Long id, Double x, Double y);

    /**
     * 点赞或取消点赞
     */
    Result likePost(Long id);

    /**
     * 查询点赞排行榜
     */
    Result queryPostLikes(Long id);

    /**
     * 保存笔记
     */
    Result savePost(Post post);

    /**
     * 查询关注者的笔记
     */
    Result queryPostOfFollow(Long max, Integer offset);

    /**
     * 根据关联商品ID查询笔记
     */
    Result queryPostByProductId(Long productId, Integer current);

    /**
     * 根据帖子分类ID查询帖子（用于首页分类筛选）
     */
    Result queryPostByCategoryId(Long categoryId, Integer current, String campus);

}
