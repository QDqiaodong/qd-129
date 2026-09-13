package com.example.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class AreaCapacityStatVO {

    private Long areaId;

    private String areaCode;

    private String areaName;

    /** 分区状态：1 启用，0 停用 */
    private Integer areaStatus;

    /** 挂出的闭馆结束时刻（可能已到期；以 closed 字段判断是否正在闭馆） */
    private LocalDateTime closedUntil;

    /** 是否正在闭馆：closedUntil 晚于当前时刻 */
    private Boolean closed;

    /** 桌椅总数（不含已删除资产） */
    private Integer totalCount;

    /** 可用桌椅数量（status=1 且未被进行中占座占住） */
    private Integer availableCount;

    /**
     * 进行中占座占用数量：被 OPEN 批次占住（在占/超时未到）的桌椅，
     * 与档案停用、报修停用分开列示，临时占用不算编制减少
     */
    private Integer occupiedCount;

    /** 停用桌椅数量（status=0 且非占座占用：档案停用、报修停用等） */
    private Integer disabledCount;

    /** 总容纳人数（按可用桌椅的 capacity 求和） */
    private Integer totalCapacity;

    /** 占座占用容纳人数（按进行中占座占用桌椅的 capacity 求和） */
    private Integer occupiedCapacity;

    /** 当前待领取遗失件数（仅 PENDING，已领取闭环不计入；按登记分区快照归集） */
    private Integer pendingLostCount;

    /** 标签构成（不含已删除资产关联） */
    private List<AreaTagStatVO> tagStats;
}
