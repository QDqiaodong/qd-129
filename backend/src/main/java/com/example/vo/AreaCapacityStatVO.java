package com.example.vo;

import lombok.Data;

import java.util.List;

@Data
public class AreaCapacityStatVO {

    private Long areaId;

    private String areaCode;

    private String areaName;

    /** 分区状态：1 启用，0 停用 */
    private Integer areaStatus;

    /** 桌椅总数（不含已删除资产） */
    private Integer totalCount;

    /** 可用桌椅数量（status=1） */
    private Integer availableCount;

    /** 停用桌椅数量（status=0） */
    private Integer disabledCount;

    /** 总容纳人数（按可用桌椅的 capacity 求和） */
    private Integer totalCapacity;

    /** 标签构成（不含已删除资产关联） */
    private List<AreaTagStatVO> tagStats;
}
