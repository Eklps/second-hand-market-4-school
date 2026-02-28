package com.hmdp;

import com.hmdp.entity.Product;
import com.hmdp.service.IProductService;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisConstants;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

@SpringBootTest
class HmDianPingApplicationTests {

    @Resource
    private IProductService productService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private CacheClient cacheClient;

    /**
     * 测试1：预热【已过期】的缓存数据
     * 用于测试缓存击穿时的异步重建逻辑
     * 运行此测试后，用 JMeter 或 Postman 访问 /product/1，会触发缓存重建
     */
    @Test
    void testSaveProductWithExpiredCache() {
        Long id = 1L;
        // 使用 CacheClient 工具类，设置逻辑过期时间为 1 秒（立即过期）
        Product product = productService.getById(id);
        cacheClient.setWithLogicExpire(RedisConstants.CACHE_SHOP_KEY + id, product, 1L, TimeUnit.SECONDS);
        System.out.println("【已过期】缓存预热成功！ID: " + id);
        System.out.println("请稍等 2 秒后再测试，确保缓存已逻辑过期...");
    }

    /**
     * 测试2：预热【未过期】的缓存数据
     * 用于测试直接命中缓存的场景
     * 运行此测试后，用 Postman 访问 /product/1，会直接返回缓存数据
     */
    @Test
    void testSaveProductWithValidCache() {
        Long id = 1L;
        // 使用 CacheClient 工具类，设置逻辑过期时间为 10 分钟（未过期）
        Product product = productService.getById(id);
        cacheClient.setWithLogicExpire(RedisConstants.CACHE_SHOP_KEY + id, product, 10L, TimeUnit.MINUTES);
        System.out.println("【未过期】缓存预热成功！ID: " + id);
        System.out.println("现在去 Postman 测试 /product/1，应该直接返回数据，不会触发重建。");
    }

    /**
     * 测试3：清除缓存
     * 用于重置测试环境
     */
    @Test
    void testClearCache() {
        Long id = 1L;
        stringRedisTemplate.delete(RedisConstants.CACHE_SHOP_KEY + id);
        stringRedisTemplate.delete(RedisConstants.LOCK_SHOP_KEY + id);
        System.out.println("缓存已清除！ID: " + id);
    }

    /**
     * 测试4：UV统计 - HyperLogLog 百万数据测试
     * 模拟100万个用户访问，验证 HyperLogLog 的去重计数精度和内存占用
     *
     * HyperLogLog 特点：
     * - 内存占用极小：最多只需要 12KB 就能统计 2^64 个不同元素
     * - 标准误差：约 0.81%
     * - 不存储元素本身，只记录基数（数量）
     */
    @Test
    void testHyperLogLog() {
        // 准备数组，装用户id数据
        String[] values = new String[1000];
        int j = 0;
        for (int i = 0; i < 1000000; i++) {
            j = i % 1000;
            values[j] = "user_" + i;
            if (j == 999) {
                // 每1000条发送一次，批量写入 HyperLogLog
                stringRedisTemplate.opsForHyperLogLog().add("hl2", values);
            }
        }
        // 统计数量
        Long count = stringRedisTemplate.opsForHyperLogLog().size("hl2");
        System.out.println("========== UV 统计测试结果 ==========");
        System.out.println("实际用户数：1000000");
        System.out.println("HyperLogLog 统计结果：" + count);
        System.out.println("误差率：" + String.format("%.2f%%", (Math.abs(count - 1000000) * 100.0 / 1000000)));
        System.out.println("=====================================");
    }
}
