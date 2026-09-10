package com.example.vo;

import com.example.entity.AreaChangeLog;
import com.example.entity.DeskChair;
import lombok.Data;

import java.util.List;

@Data
public class AreaCapacityDetailVO {

    private Long areaId;

    private String areaCode;

    private String areaName;

    private Integer areaStatus;

    private String description;

    private Integer totalCount;

    private Integer availableCount;

    private Integer disabledCount;

    private Integer totalCapacity;

    /** 分区内桌椅明细（不含已删除资产，含停用资产） */
    private List<DeskChair> deskChairs;

    /** 标签构成 */
    private List<AreaTagStatVO> tagStats;

    /** 最近调区变更记录 */
    private List<AreaChangeLog> recentChanges;
}
