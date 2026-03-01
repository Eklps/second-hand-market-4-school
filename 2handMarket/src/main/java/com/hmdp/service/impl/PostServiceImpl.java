package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.entity.Follow;
import org.springframework.data.redis.core.ZSetOperations;
import com.hmdp.dto.Result;
import com.hmdp.dto.ScrollResult;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Post;
import com.hmdp.entity.User;
import com.hmdp.mapper.PostMapper;
import com.hmdp.service.IPostService;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.concurrent.TimeUnit;
import com.hmdp.utils.RedisConstants;
import org.springframework.data.geo.Point;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class PostServiceImpl extends ServiceImpl<PostMapper, Post> implements IPostService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private IUserService userService;

    @Resource
    private IFollowService followService;

    @Override
    public Result savePost(Post post) {
        // 1.获取登录用户
        UserDTO user = UserHolder.getUser();
        post.setUserId(user.getId());
        // 2.保存探店笔记
        boolean isSuccess = save(post);
        if (!isSuccess) {
            return Result.fail("新增笔记失败");
        }

        // 3. 将地理位置信息同步到 Redis GEO (仅当坐标存在时)
        if (post.getX() != null && post.getY() != null) {
            stringRedisTemplate.opsForGeo().add(
                    RedisConstants.POST_GEO_KEY,
                    new Point(post.getX(), post.getY()),
                    post.getId().toString());
        }

        // 4.查询笔记作者的所有粉丝
        // select * from tb_follow where follow_user_id = ?
        List<Follow> follows = followService.query()
                .eq("follow_user_id", user.getId())
                .list();
        // 5.推送笔记id给所有粉丝
        for (Follow follow : follows) {
            // 5.1获取粉丝id
            Long userId = follow.getUserId();
            // 5.2推送到粉丝的收件箱(RedisSortedSet)
            String key = "feed:" + userId;
            stringRedisTemplate.opsForZSet()
                    .add(key, post.getId().toString(), System.currentTimeMillis());
        }
        // 返回id

        return Result.ok(post.getId());
    }

    @Override
    public Result queryHotPost(Integer current, Double x, Double y, String campus) {
        // 1.根据点赞数降序查询
        com.baomidou.mybatisplus.extension.conditions.query.QueryChainWrapper<Post> queryWrapper = query();
        // 如果传入了校区且不是“全部”，则增加过滤条件
        if (StrUtil.isNotBlank(campus) && !"全部".equals(campus)) {
            queryWrapper.eq("campus", campus);
        }

        Page<Post> page = queryWrapper.orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        List<Post> records = page.getRecords();

        if (records == null || records.isEmpty()) {
            return Result.ok();
        }

        // 2.遍历，填充基础信息
        records.forEach(post -> {
            this.queryPostUser(post);
            this.isPostLiked(post);
            // 3. 如果经纬度不为空，计算距离显示
            if (x != null && y != null && post.getX() != null && post.getY() != null) {
                double distance = calculateDistance(x, y, post.getX(), post.getY());
                post.setDistance(distance);
            }
        });
        return Result.ok(records);
    }

    private double calculateDistance(double x1, double y1, double x2, double y2) {
        // Haversine formula (简单实现，返回米)
        double R = 6371000; // 地球半径
        double dLat = Math.toRadians(y2 - y1);
        double dLon = Math.toRadians(x2 - x1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(y1)) * Math.cos(Math.toRadians(y2)) *
                        Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return Math.round(R * c); // 返回整数距离
    }

    @Override
    public Result likePost(Long id) {
        // 1.获取当前登陆的用户
        Long userId = UserHolder.getUser().getId();
        // 2.查看当前用户是否已经进行过点赞
        // key="post:liked"+postId
        String key = "post:liked:" + id;
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());

        if (score == null) {
            // 3.如果没点过赞，进行点赞操作
            // 3.1数据库点赞数++
            boolean isSuccess = update().setSql("liked= liked +1").eq("id", id).update();
            // 3.2保存用户到Redis的SortedSet集合，
            if (isSuccess) {
                // zadd key score member
                // score使用当前的时间戳，方便后面点赞按时间排序
                stringRedisTemplate.opsForZSet().add(key, userId.toString(), System.currentTimeMillis());
            }

        } else {
            // 4.如果已点赞，取消点赞
            // 4.1数据库点赞数-1
            boolean isSuccess = update().setSql("liked=liked-1").eq("id", id).update();
            // 4.2把用户从Redis的set集合移除
            if (isSuccess) {
                stringRedisTemplate.opsForZSet().remove(key, userId.toString());
            }
        }

        return Result.ok();
    }

    // 这个是查看博客的点赞排行榜
    @Override
    public Result queryPostLikes(Long id) {
        String key = "post:liked:" + id;
        // 1.查询top5点赞用户 zrange key 0 4
        Set<String> top5 = stringRedisTemplate.opsForZSet().range(key, 0, 4);
        // 如果没人点赞，直接返回空列表
        if (top5 == null || top5.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }

        // 2.解析出其中的用户id
        // 先把Set<String>转换为List<Long>
        List<Long> ids = top5.stream().map(Long::valueOf).collect(Collectors.toList());
        // 然后转为带","的String好传入database
        String idStr = StrUtil.join(",", ids);

        // 3.根据用户id查询用户WHERE id IN (5,1) ORDER BY FIELD(id,5,1)
        // 这里的5,1只是举例，举例用户id是5，1（有顺序）
        // 这里必须用last("ORDER BY FIELD...")来保证顺序
        /**
         * SQL 的 IN 查询会自动按主键 ID 升序排序，这会弄乱 Redis 里的时间顺序
         * last 方法会将字符串原封不动拼接到生成的 SQL 语句最后。
         * 作用：强制 MySQL 按照 idStr（如 "5,1,3..."）给定的顺序返回结果。
         * .list()：
         * 执行者：执行最终查询，返回一个 List<User>。
         * .stream()将刚刚的List<User>转换为流
         * .map(...):流里面的每一个元素都是User对象，我们要把User数据脱敏为UserDTO
         * .collect... 将脱敏出来的数据流再重新装成List
         */
        List<UserDTO> userDTOS = userService.query().in("id", ids)
                .last("ORDER BY FIELD(id," + idStr + ")").list()
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());

        // 4.返回
        return Result.ok(userDTOS);
    }

    @Override
    public Result queryPostById(Long id, Double x, Double y) {
        String key = RedisConstants.CACHE_POST_KEY + id;

        // 1. 从 redis 尝试命中缓存
        String postJson = stringRedisTemplate.opsForValue().get(key);
        Post post = null;

        // 2. 处理缓存命中
        if (StrUtil.isNotBlank(postJson)) {
            post = JSONUtil.toBean(postJson, Post.class);
        } else if (postJson != null) {
            // 【防穿透】
            return Result.fail("您访问的帖子好像被火星人劫持了！");
        } else {
            // 3. 缓存未命中，重建缓存 (防击穿)
            String lockKey = RedisConstants.LOCK_POST_KEY + id;
            try {
                boolean isLock = tryLock(lockKey);
                if (!isLock) {
                    Thread.sleep(50);
                    return queryPostById(id, x, y);
                }
                post = getById(id);
                if (post == null) {
                    stringRedisTemplate.opsForValue().set(key, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
                    return Result.fail("您访问的帖子好像被火星人劫持了！");
                }
                long ttl = RedisConstants.CACHE_POST_TTL + RandomUtil.randomLong(0, 5);
                stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(post), ttl, TimeUnit.MINUTES);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            } finally {
                unlock(lockKey);
            }
        }

        // 4. 填充作者、点赞、浏览量
        queryPostUser(post);
        isPostLiked(post);
        recordAndSetUv(post);

        // 5. 【新增】计算传入坐标与帖子的距离
        if (x != null && y != null && post.getX() != null && post.getY() != null) {
            double distance = calculateDistance(x, y, post.getX(), post.getY());
            post.setDistance(distance);
        }

        return Result.ok(post);
    }

    private void recordAndSetUv(Post post) {
        String uvKey = "post:uv:" + post.getId();
        UserDTO user = UserHolder.getUser();
        if (user != null) {
            stringRedisTemplate.opsForHyperLogLog().add(uvKey, user.getId().toString());
        } else {
            stringRedisTemplate.opsForHyperLogLog().add(uvKey, "anonymous_" + System.nanoTime());
        }
        Long viewCount = stringRedisTemplate.opsForHyperLogLog().size(uvKey);
        post.setViewCount(viewCount);
    }

    private boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    private void unlock(String key) {
        stringRedisTemplate.delete(key);
    }

    @Override
    public Result queryPostOfFollow(Long max, Integer offset) {
        // 有一个误区，这是向下滚动，而非上拉刷新；想下滚动也就是把截止到当前时刻的动态都查出来，若插入新的动态这里是看不到的，需要上拉刷新
        // 1.获取当前用户
        Long userId = UserHolder.getUser().getId();

        // 2.查询收件箱
        // ZREVRSNGERBYSCORE key max 0 LIMIT offset count
        String key = "feed:" + userId;
        Set<ZSetOperations.TypedTuple<String>> typedTuples = stringRedisTemplate.opsForZSet()
                .reverseRangeByScoreWithScores(key, 0, max, offset, 2);// 2为每页条数
        // max为本次查询记录的最大时间戳

        // 3.非空判断
        if (typedTuples == null || typedTuples.isEmpty()) {
            return Result.ok();
        }

        // 4.解析出postId,minTime,offset
        List<Long> ids = new ArrayList<>(typedTuples.size());
        long minTime = 0;// 本次查询记录的最小时间戳
        int os = 1;// 统计与最小时间戳相同的记录个数

        for (ZSetOperations.TypedTuple<String> tuple : typedTuples) {
            // 4.1获取id
            ids.add(Long.valueOf(tuple.getValue()));
            // 4.2获取score
            long time = tuple.getScore().longValue();
            if (time == minTime) {
                os++;
            } else {
                minTime = time;
                os = 1;
            }
        }

        // 5.根据id查询post (ps：必须按顺序查)
        String idStr = StrUtil.join(",", ids);
        List<Post> posts = query().in("id", ids).last("ORDER BY FIELD(id," + idStr + ")").list();

        // 6.每一篇博客填充用户信息和点赞状态
        for (Post post : posts) {
            queryPostUser(post);
            isPostLiked(post);
        }

        // 7.封装返回
        ScrollResult r = new ScrollResult();
        r.setList(posts);
        r.setMinTime(minTime);
        r.setOffset(os);
        return Result.ok(r);

    }

    @Override
    public Result queryPostByProductId(Long productId, Integer current) {
        // 根据 related_id 查询帖子（即对该商品的相关留言或评价贴）
        Page<Post> page = query()
                .eq("related_id", productId)
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));

        List<Post> records = page.getRecords();
        if (records == null || records.isEmpty()) {
            return Result.ok();
        }

        // 补充每个帖子的发帖人（name/icon）和点赞状态
        records.forEach(post -> {
            this.queryPostUser(post);
            this.isPostLiked(post);
            // 顺便做个简单处理避免空指针
            if (post.getImages() == null) {
                post.setImages("");
            }
        });

        return Result.ok(records);
    }

    @Override
    public Result queryPostByCategoryId(Long categoryId, Integer current, String campus) {
        com.baomidou.mybatisplus.extension.conditions.query.QueryChainWrapper<Post> queryWrapper = query()
                .eq("category_id", categoryId);

        // 如果校区不为空且不是“全部”，则增加校区过滤
        if (StrUtil.isNotBlank(campus) && !"全部".equals(campus)) {
            queryWrapper.eq("campus", campus);
        }

        Page<Post> page = queryWrapper.orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        List<Post> records = page.getRecords();
        if (records == null || records.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        records.forEach(post -> {
            this.queryPostUser(post);
            this.isPostLiked(post);
            if (post.getImages() == null)
                post.setImages("");
        });
        return Result.ok(records);
    }

    // 一个相关的用法，用于查询并填充作者信息
    private void queryPostUser(Post post) {
        Long userId = post.getUserId();
        User user = userService.getById(userId);
        post.setName(user.getNickName());
        post.setIcon(user.getIcon());
    }

    // 辅助方法：判断是否已经点赞，用于返回给前端的显示
    private void isPostLiked(Post post) {
        // 1.获取登录用户
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            // 用户未登录，无需查询是否点赞
            return;
        }
        Long userId = user.getId();

        // 2.判断当前登录用户是否已经点赞
        String key = "post:liked:" + post.getId();
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        // 3.如果score不为null,说明已经点过赞，将isLike设置为true
        post.setIsLike(score != null);
    }
}
