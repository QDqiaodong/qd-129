package com.example.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 清场清单行：整批结束后仍停用桌椅的一件，
 * 携带占座明细状态、桌椅实时状态与未闭环报修信息，供清场分组与逐条释放对照。
 */
@Data
public class SeatHoldClearingItemVO {

    /** 占座明细 ID，清场释放时回传 */
    private Long itemId;

    private Long deskChairId;

    private String assetCode;

    /** HOLDING 在占 / TIMEOUT 超时未到 / RELEASED 已释放 */
    private String itemStatus;

    /** 占住前桌椅启用状态，释放时按此回写 */
    private Integer previousDeskStatus;

    /** 桌椅档案实时状态，清场清单只收录仍为停用（0）的 */
    private Integer deskStatus;

    /** 未闭环（待接单/维修中）报修单数，>0 时释放后桌椅仍保持停用 */
    private Integer openRepairCount;

    /** 未闭环报修单号，逗号拼接，便于清场时对照档案 */
    private String openRepairOrderNos;

    /** 明细未闭环（在占/超时未到）时可逐条清场释放 */
    private Boolean releasable;

    private String releasedBy;

    private LocalDateTime releasedAt;

    private String timeoutBy;

    private LocalDateTime timeoutAt;
}
