package com.hmdp.config;

import com.hmdp.entity.Product;
import com.hmdp.service.IProductService;
import com.hmdp.utils.RedisConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 应用启动时，将商铺坐标数据加载到 Redis GEO 中
 * Key 格式: product:geo:{typeId}
 * Value: productId
 * Score: 经纬度
 */
@Slf4j
@Component
public class ProductGeoDataLoader implements CommandLineRunner {

    @Resource
    private IProductService productService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public void run(String... args) {
        // 1. 查询所有商铺信息
        List<Product> list = productService.list();

        // 2. 按照 typeId 分组
        Map<Long, List<Product>> map = list.stream().collect(Collectors.groupingBy(Product::getCategoryId));

        // 3. 分批写入 Redis
        for (Map.Entry<Long, List<Product>> entry : map.entrySet()) {
            // 3.1 获取类型id
            Long typeId = entry.getKey();
            String key = RedisConstants.SHOP_GEO_KEY + typeId;
            // 3.2 获取同类型的店铺集合
            List<Product> products = entry.getValue();

            // 3.3 批量写入 Redis GEO（比逐条写入性能更好）
            List<RedisGeoCommands.GeoLocation<String>> locations = new ArrayList<>(products.size());
            for (Product product : products) {
                locations.add(new RedisGeoCommands.GeoLocation<>(
                        product.getId().toString(),
                        new Point(product.getX(), product.getY())));
            }
            stringRedisTemplate.opsForGeo().add(key, locations);
        }
        log.debug("商铺GEO数据加载完成，共加载 {} 条数据", list.size());
    }
}
