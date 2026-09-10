package com.example.vo;

import lombok.Data;

@Data
public class AreaChangeTrendVO {

    /** 日期，格式 yyyy-MM-dd */
    private String date;

    /** 当日调区变更条数（不含已删除资产产生的记录） */
    private Integer changeCount;
}
