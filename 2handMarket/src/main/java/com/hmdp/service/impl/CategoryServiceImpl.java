package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.Category;
import com.hmdp.mapper.CategoryMapper;
import com.hmdp.service.ICategoryService;
import com.hmdp.utils.RedisConstants;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class CategoryServiceImpl extends ServiceImpl<CategoryMapper, Category> implements ICategoryService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public List<Category> queryTypeList() {
        String key = RedisConstants.CACHE_SHOP_TYPE_KEY;
        // 1.先查询redis看有没有
        String json = stringRedisTemplate.opsForValue().get(key);

        if (StrUtil.isNotBlank(json)) {
            return JSONUtil.toList(json, Category.class);
        }
        // 2.redis没有再查询数据库
        List<Category> list = list();

        // 3.把数据从数据库存入redis
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(list), RedisConstants.CACHE_SHOP_TYPE_KEY_TTL,
                TimeUnit.HOURS);
        return list;
    }
}
