package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.Voucher;
import com.hmdp.mapper.VoucherMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import javax.annotation.PostConstruct;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;
import cn.hutool.core.util.RandomUtil;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherServiceImpl extends ServiceImpl<VoucherMapper, Voucher> implements IVoucherService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private IVoucherOrderService voucherOrderService;

    @PostConstruct
    public void initSeckillStock() {
        // 在系统启动时自动加载目前所有秒杀库存进Redis，防止后台人工向数据库插数据而没被预热引发抢购报错
        List<SeckillVoucher> list = seckillVoucherService.list();
        if (list != null) {
            LocalDateTime now = LocalDateTime.now();
            for (SeckillVoucher sv : list) {
                String stock = String.valueOf(sv.getStock());
                // 【核心优化点：缓解缓存雪崩与缓存击穿】
                // 1. 设置 TTL，防内存堆积(OOM)。
                // 2. TTL 不能太短，必须覆盖【活动结束时间】，否则活动期间缓存过期会引发大量请求并发查库（缓存击穿）。
                long durationMillis = 0;
                if (now.isBefore(sv.getEndTime())) {
                    durationMillis = Duration.between(now, sv.getEndTime()).toMillis();
                }
                // 3. 在活动时间基础上，额外增加随机的 0~120 小时的延时（打散 TTL）！
                // 这能彻底打破多个秒杀活动在同一时刻集中过期而直接压垮数据库的情形（缓存雪崩）。
                long randomMillis = RandomUtil.randomLong(0, 1000 * 60 * 60 * 120);
                long ttlMillis = durationMillis + randomMillis;

                stringRedisTemplate.opsForValue().set(RedisConstants.SEKILL_STOCK_KEY + sv.getVoucherId(), stock,
                        ttlMillis, TimeUnit.MILLISECONDS);
            }
            System.out.println("====== 秒杀库存已全量预热至 Redis 中并附加防雪崩打散机制，预热条数: " + list.size() + " ======");
        }
    }

    @Override
    public Result queryVoucherOfProduct(Long productId) {
        // 1.查询优惠券信息
        List<Voucher> vouchers = getBaseMapper().queryVoucherOfProduct(productId);
        // 2.如果用户已登录，查询用户是否已经抢过这些券，以便前端置灰
        Long userId = UserHolder.getUser() == null ? null : UserHolder.getUser().getId();
        if (userId != null && vouchers != null) {
            for (Voucher v : vouchers) {
                if (v.getType() == 1) { // 仅限秒杀券
                    // 高并发极致优化：不再查数据库，直接去 Redis 的 Set 集合里确认用户是否在抢购名单中
                    String key = "sekill:order" + v.getId(); // 注意：这里的 key 必须和 seckill.lua 中保持完全一致
                    Boolean isMember = stringRedisTemplate.opsForSet().isMember(key, userId.toString());
                    v.setIsClaimed(Boolean.TRUE.equals(isMember));
                }
            }
        }
        // 3.返回结果
        return Result.ok(vouchers);
    }

    @Override
    @Transactional
    public void addSeckillVoucher(Voucher voucher) {
        // 1. 保存优惠券基本信息到 tb_voucher
        save(voucher);

        // 2. 保存秒杀券信息到 tb_seckill_voucher
        SeckillVoucher seckillVoucher = new SeckillVoucher();
        seckillVoucher.setVoucherId(voucher.getId());
        seckillVoucher.setStock(voucher.getStock());
        seckillVoucher.setBeginTime(voucher.getBeginTime());
        seckillVoucher.setEndTime(voucher.getEndTime());
        seckillVoucherService.save(seckillVoucher);

        // 3.redis优化，添加秒杀库存到redis中，并设定随机过期时间防雪崩
        long durationMillis = 0;
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(voucher.getEndTime())) {
            durationMillis = Duration.between(now, voucher.getEndTime()).toMillis();
        }
        long randomMillis = RandomUtil.randomLong(0, 1000 * 60 * 60 * 120);
        long ttlMillis = durationMillis + randomMillis;
        stringRedisTemplate.opsForValue().set(RedisConstants.SEKILL_STOCK_KEY + voucher.getId(),
                voucher.getStock().toString(),
                ttlMillis, TimeUnit.MILLISECONDS);

    }
}
