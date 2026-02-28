package com.hmdp.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("tb_product")
public class Product implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 商品标题
     */
    private String title;

    /**
     * 分类id
     */
    private Long categoryId;

    /**
     * 商铺图片，多个图片以','隔开
     */
    private String images;

    /**
     * 校区
     */
    private String campus;

    /**
     * 交易地址
     */
    private String location;

    /**
     * 经度
     */
    private Double x;

    /**
     * 维度
     */
    private Double y;

    /**
     * 售价，取整数(分)
     */
    private Long price;

    /**
     * 原价
     */
    private Long originalPrice;

    /**
     * 商品状态：0待售，1已预订，2已售出，3已下架
     */
    private Integer status;

    /**
     * 评论数量
     */
    private Integer comments;

    /**
     * 商品描述
     */
    private String description;

    /**
     * 卖家id
     */
    private Long userId;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    @TableField(exist = false)
    private Double distance;
}
