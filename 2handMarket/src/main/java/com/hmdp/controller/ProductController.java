package com.hmdp.controller;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.entity.Product;
import com.hmdp.service.IProductService;
import com.hmdp.utils.SystemConstants;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/product")
public class ProductController {

    @Resource
    public IProductService productService;

    /**
     * 根据id查询商铺信息
     * 
     * @param id 商铺id
     * @return 商铺详情数据
     */
    @GetMapping("/{id}")
    public Result queryProductById(@PathVariable("id") Long id) {
        return productService.queryById(id);
    }

    /**
     * 新增商铺信息
     * 
     * @param product 商铺数据
     * @return 商铺id
     */
    @PostMapping
    public Result saveProduct(@RequestBody Product product) {
        // 写入数据库
        productService.save(product);
        // 返回店铺id
        return Result.ok(product.getId());
    }

    /**
     * 更新商铺信息
     * 
     * @param product 商铺数据
     * @return 无
     */
    @PutMapping
    public Result updateProduct(@RequestBody Product product) {
        // 写入数据库

        return productService.updateProductById(product);
    }

    /**
     * 根据商铺类型分页查询商铺信息
     * 
     * @param typeId  商铺类型
     * @param current 页码
     * @param x       经度（可选）
     * @param y       纬度（可选）
     * @return 商铺列表
     */
    @GetMapping("/of/type")
    public Result queryProductByType(
            @RequestParam("typeId") Integer typeId,
            @RequestParam(value = "current", defaultValue = "1") Integer current,
            @RequestParam(value = "x", required = false) Double x,
            @RequestParam(value = "y", required = false) Double y) {
        return productService.queryProductByType(typeId, current, x, y);
    }

    /**
     * 根据商铺名称关键字分页查询商铺信息
     * 
     * @param name    商铺名称关键字
     * @param current 页码
     * @return 商铺列表
     */
    @GetMapping("/of/title")
    public Result queryProductByTitle(
            @RequestParam(value = "title", required = false) String title,
            @RequestParam(value = "current", defaultValue = "1") Integer current) {
        // 根据类型分页查询
        Page<Product> page = productService.query()
                .like(StrUtil.isNotBlank(title), "title", title)
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 返回数据
        return Result.ok(page.getRecords());
    }
}
