package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IUserInfoService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private IUserService userService;

    @Resource
    private IUserInfoService userInfoService;

    @Override
    @Transactional
    public Result follow(Long followUserId, Boolean isFollow) {
        // 1.获取登录用户
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.fail("请先登录后操作");
        }
        Long userId = user.getId();
        String key = "follows:" + userId;
        // 2.判断关注还是取关
        if (isFollow) {
            // 2.1关注，新增数据
            Follow follow = new Follow();
            follow.setUserId(userId);
            follow.setFollowUserId(followUserId);
            boolean isSuccess = save(follow);
            if (isSuccess) {
                // 把关注用户的id，放入Redis的Set集合 sadd userId followUserId
                stringRedisTemplate.opsForSet().add(key, followUserId.toString());
                // 同步更新统计数据：关注者 followee +1, 被关注者 fans +1
                userInfoService.update().setSql("followee = followee + 1").eq("user_id", userId).update();
                userInfoService.update().setSql("fans = fans + 1").eq("user_id", followUserId).update();
            }
        } else {
            // 2.2取关，删除
            boolean isSuccess = remove(new QueryWrapper<Follow>()
                    .eq("user_id", userId)
                    .eq("follow_user_id", followUserId));
            if (isSuccess) {
                // 在这里把关注关系从RedisSet删除
                stringRedisTemplate.opsForSet().remove(key, followUserId.toString());
                // 同步更新统计数据：关注者 followee -1, 被关注者 fans -1
                userInfoService.update().setSql("followee = followee - 1").eq("user_id", userId).update();
                userInfoService.update().setSql("fans = fans - 1").eq("user_id", followUserId).update();
            }
        }
        return Result.ok();
    }

    @Override
    public Result isFollow(Long followUserId) {
        // 1.获取登录用户
        Long userId = UserHolder.getUser().getId();
        // 2. 查询是否关注
        Long count = query().eq("user_id", userId)
                .eq("follow_user_id", followUserId)
                .count();
        // 3. 返回判断
        return Result.ok(count > 0);
    }

    @Override
    public Result followCommons(Long id) {
        // 1.获取当前用户
        Long userId = UserHolder.getUser().getId();
        String key1 = "follows:" + userId;
        // 求交集
        String key2 = "follows:" + id;
        Set<String> intersect = stringRedisTemplate.opsForSet().intersect(key1, key2);

        if (intersect == null || intersect.isEmpty()) {
            // 无交集
            return Result.ok(Collections.emptyList());
        }

        // 3.有交集，解析id集合
        List<Long> ids = intersect.stream().map(Long::valueOf).collect(Collectors.toList());

        // 4.查询用户并转化为 DTO 列表
        List<UserDTO> users = userService.listByIds(ids).stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        return Result.ok(users);
    }

    @Override
    public Result queryFollowers(Integer current) {
        Long userId = UserHolder.getUser().getId();
        // 1. 查询粉丝列表（谁关注了我）
        Page<Follow> page = query().eq("follow_user_id", userId)
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        List<Follow> records = page.getRecords();
        if (records == null || records.isEmpty()) {
            return Result.ok(Collections.emptyList(), page.getTotal());
        }
        // 2. 获取用户ID
        List<Long> ids = records.stream().map(Follow::getUserId).collect(Collectors.toList());
        // 3. 查询用户信息
        List<UserDTO> users = userService.listByIds(ids).stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        // 4. 返回包含总计和列表的数据
        return Result.ok(users, page.getTotal());
    }

    @Override
    public Result queryFollowings(Integer current) {
        Long userId = UserHolder.getUser().getId();
        // 1. 查询我关注的人
        Page<Follow> page = query().eq("user_id", userId)
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        List<Follow> records = page.getRecords();
        if (records == null || records.isEmpty()) {
            return Result.ok(Collections.emptyList(), page.getTotal());
        }
        // 2. 获取用户ID
        List<Long> ids = records.stream().map(Follow::getFollowUserId).collect(Collectors.toList());
        // 3. 查询用户信息
        List<UserDTO> users = userService.listByIds(ids).stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        // 4. 返回包含总计和列表的数据
        return Result.ok(users, page.getTotal());
    }
}
