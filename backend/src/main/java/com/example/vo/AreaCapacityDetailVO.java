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

    /** 进行中占座占用数量（在占/超时未到），与档案停用、报修停用分开列示 */
    private Integer occupiedCount;

    private Integer disabledCount;

    private Integer totalCapacity;

    /** 占座占用容纳人数 */
    private Integer occupiedCapacity;

    /** 当前待领取遗失件数（仅 PENDING，已领取闭环不计入） */
    private Integer pendingLostCount;

    /** 待领取遗失单清单（单号、物品名称、桌椅编号等，按登记时间倒序） */
    private List<LostItem> pendingLostItems;

    /** 分区内桌椅明细（不含已删除资产，含停用资产） */
    private List<DeskChair> deskChairs;

    /** 标签构成 */
    private List<AreaTagStatVO> tagStats;

    /** 最近调区变更记录 */
    private List<AreaChangeLog> recentChanges;
}
