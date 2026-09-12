package com.example.vo;

import lombok.Data;

@Data
public class BatchTransferItemVO {

    private Long deskChairId;

    private String assetCode;

    private String dimensions;

    private Long oldAreaId;

    private String oldAreaName;

    private Long newAreaId;

    private String newAreaName;

    private Boolean valid;

    private String errorMessage;

    private String status;

    /**
     * 不可迁移原因码：NOT_FOUND（资产已删除）/ DISABLED（档案停用）/
     * SEAT_HOLDING（进行中占座批次占住）/ ALREADY_IN_TARGET（已在目标分区）
     */
    private String reasonCode;

    /**
     * 原因码为 SEAT_HOLDING 时，占住该资产的进行中批次号，
     * 供前端与占座列表逐项核对
     */
    private String seatHoldBatchNo;
}
