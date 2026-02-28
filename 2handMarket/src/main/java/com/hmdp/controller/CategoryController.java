package com.hmdp.controller;

import com.hmdp.dto.Result;
import com.hmdp.entity.Category;
import com.hmdp.service.ICategoryService;
import com.hmdp.service.impl.CategoryServiceImpl;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/category")
public class CategoryController {
    @Resource
    private ICategoryService typeService;

    @GetMapping("list")
    public Result queryTypeList() {
        // List<Category> typeList = typeService
        // .query().orderByAsc("sort").list();
        List<Category> typeList = typeService.queryTypeList();
        return Result.ok(typeList);
    }
}
