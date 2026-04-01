package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.Product;
import com.hmdp.mapper.ProductMapper;
import com.hmdp.service.IProductService;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisData;
import com.hmdp.utils.SystemConstants;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ProductServiceImpl extends ServiceImpl<ProductMapper, Product> implements IProductService {
    public static final String LOCK_SHOP_KEY = RedisConstants.LOCK_SHOP_KEY;
    public static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private CacheClient cacheClient;

    @Resource
    private RedissonClient redissonClient;

    private RBloomFilter<Long> productBloomFilter;

    @PostConstruct
    public void initBloomFilter() {
        productBloomFilter = redissonClient.getBloomFilter("product:bloom:filter");
        // 初始化布隆过滤器（预计容量10000，误差率0.01）
        productBloomFilter.tryInit(10000L, 0.01);

        // 查询所有的商品id以预热布隆过滤器
        List<Product> products = query().select("id").list();
        if (products != null) {
            for (Product product : products) {
                productBloomFilter.add(product.getId());
            }
        }
    }

    @Override
    public Result queryById(Long id) {
        // 使用缓存穿透的工具类
        // 这里的id2->getById(id2)可以改写为
        /**
         * this::getById
         **/
        // Product product =
        // cacheClient.queryWithPssThrough(RedisConstants.CACHE_SHOP_KEY,
        // id, Product.class, id2 -> getById(id2),
        // RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);

        // 使用缓存击穿的工具类
        Product product = cacheClient.queryWithLogicExpire(RedisConstants.CACHE_SHOP_KEY, id, Product.class,
                this::getById,
                RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES, productBloomFilter);

        // 最开始的缓存穿透的调用方法
        // Product product=queryWithCacheThrough(id);

        // 使用应对缓存击穿的解法：互斥锁
        // Product product = queryWithMutex(id);

        // 缓存击穿的写法
        // 设置逻辑过期时间来应对缓存击穿
        // Product product=queryWithLogicExpire(id);
        return Result.ok(product);
    }

    public Product queryWithMutex(Long id) {
        String key = RedisConstants.CACHE_SHOP_KEY + id;
        // 1.从redis查询商铺缓存
        String productJson = stringRedisTemplate.opsForValue().get(key);
        // 2.判断缓存是否命中
        if (StrUtil.isNotBlank(productJson)) {
            // 3.存在直接返回前端
            Product product = JSONUtil.toBean(productJson, Product.class);
            return product;
        }

        // 缓存穿透检查
        if ("".equals(productJson)) {
            return null;
        }

        // 4.实现缓存重建
        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
        Product product = null;
        try {
            // 4.1 尝试获取互斥锁
            if (!tryLock(lockKey)) {
                // 4.2 获取失败，休眠
                Thread.sleep(50L);
                return queryWithMutex(id);
            }
            // 4.3获取成功，再次检查缓存（Double Check）
            productJson = stringRedisTemplate.opsForValue().get(key);
            // 缓存命中正常数据
            if (StrUtil.isNotBlank(productJson)) {
                return JSONUtil.toBean(productJson, Product.class);
            }
            // 缓存命中空值（防穿透标记），直接返回不查库
            if ("".equals(productJson)) {
                return null;
            }
            // 真正未命中，继续往下查库

            // 5.获取失败，转接给数据库看看是否存在数据
            product = getById(id);// mybatis的便捷操作，getById()

            // 模拟重建延时
            Thread.sleep(500);

            if (product == null) {
                // 6.不存在，将这个key和对应的空值存入redis以防缓存穿透
                stringRedisTemplate.opsForValue().set(key, "", RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
                // 返回错误,终结本次穿透
                return null;
            }
            // 7.存在，将商铺信息写入redis
            stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(product), RedisConstants.CACHE_SHOP_TTL,
                    TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            // 8.释放互斥锁
            unLock(lockKey);
        }

        return product;
    }

    @Override
    public Result queryProductByType(Integer typeId, Integer current, Double x, Double y) {
        // 1. 判断是否需要根据坐标查询
        if (x == null || y == null) {
            // 不需要坐标查询，按数据库查询
            Page<Product> page = query()
                    .eq("category_id", typeId)
                    .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
            return Result.ok(page.getRecords());
        }

        // 2. 计算分页参数
        int from = (current - 1) * SystemConstants.DEFAULT_PAGE_SIZE;
        int end = current * SystemConstants.DEFAULT_PAGE_SIZE;

        // 3. 查询redis，按照距离排序、分页。结果：productId、distance
        String key = RedisConstants.SHOP_GEO_KEY + typeId;
        // GEOSEARCH key BYLONLAT x y BYRADIUS 5000 m WITHDIST COUNT end ASC
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringRedisTemplate.opsForGeo()
                .search(
                        key,
                        GeoReference.fromCoordinate(x, y),
                        new Distance(5000),
                        RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs().includeDistance().limit(end));

        // 4. 解析出id
        if (results == null) {
            return Result.ok(Collections.emptyList());
        }
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> list = results.getContent();
        if (list.size() <= from) {
            // 没有下一页了，结束
            return Result.ok(Collections.emptyList());
        }

        // 4.1 截取 from ~ end 的部分（手动分页）
        List<Long> ids = new ArrayList<>(list.size());
        Map<String, Distance> distanceMap = new HashMap<>(list.size());
        list.stream().skip(from).forEach(result -> {
            // 4.2 获取店铺id
            String productIdStr = result.getContent().getName();
            ids.add(Long.valueOf(productIdStr));
            // 4.3 获取距离
            distanceMap.put(productIdStr, result.getDistance());
        });

        // 5. 根据id查询Product
        String idStr = StrUtil.join(",", ids);
        List<Product> products = query().in("id", ids).last("ORDER BY FIELD(id," + idStr + ")").list();

        // 6. 给每个product设置distance字段
        for (Product product : products) {
            product.setDistance(distanceMap.get(product.getId().toString()).getValue());
        }

        // 7. 返回
        return Result.ok(products);
    }

    @Transactional
    @Override
    public Result updateProductById(Product product) {
        // 判断商店id为不为空
        Long id = product.getId();
        if (id == null) {
            return Result.fail("商铺id不能为空");
        }
        // 1.更新数据库
        updateById(product);

        // 2.删除缓存
        stringRedisTemplate.delete(RedisConstants.CACHE_SHOP_KEY + product.getId());
        return Result.ok();
    }

    @Override
    public boolean save(Product entity) {
        boolean saved = super.save(entity);
        if (saved && productBloomFilter != null) {
            productBloomFilter.add(entity.getId());
        }
        return saved;
    }

    // 建立互斥锁
    private boolean tryLock(String keyLock) {
        Boolean bool = stringRedisTemplate.opsForValue().setIfAbsent(keyLock, "1", RedisConstants.LOCK_SHOP_TTL,
                TimeUnit.MINUTES);
        return BooleanUtil.isTrue(bool);
    }

    // 释放互斥锁
    private void unLock(String keyLock) {
        if (BooleanUtil.isTrue(stringRedisTemplate.hasKey(keyLock))) {
            stringRedisTemplate.delete(keyLock);
        }
    }

    /**
     * 将商铺信息转为带有逻辑TTL的对象存入redis
     * 
     * @param id            商铺id，数据库根据这个查询
     * @param expireSeconds 逻辑过期时间管理人员设置的过期时间
     */
    // private void saveProduct2Redis(Long id, Long expireSeconds) {
    // // 1.查询商铺信息
    // Product product = getById(id);

    // // 2.封装RedisData，也就是包含逻辑过期时间
    // RedisData redisData = new RedisData();
    // redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
    // redisData.setData(product);
    // // 3.写入redis
    // stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + id,
    // JSONUtil.toJsonStr(redisData));
    // }
}
