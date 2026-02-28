package com.hmdp.dto;

import lombok.Data;

import java.util.List;

@Data
public class ScrollResult {
    private List<?> list; // 本次查出来的动态列表
    private Long minTime; // 本次查出来的动态列表的最小时间
    private Integer offset; // 本次查出来的动态列表的偏移量（用来跳过与minTime相同的动态）
}
