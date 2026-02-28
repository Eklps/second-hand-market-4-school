package com.hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.dto.Result;
import com.hmdp.entity.Product;

/**
 * <p>
 * 服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IProductService extends IService<Product> {

    Result queryById(Long id);

    Result updateProductById(Product product);

    Result queryProductByType(Integer typeId, Integer current, Double x, Double y);

    // boolean tryLock(String lockKey);
    //
    // void unLock(String lockKey);

}
