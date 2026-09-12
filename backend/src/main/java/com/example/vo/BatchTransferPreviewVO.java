package com.example.vo;

import lombok.Data;

import java.util.List;

@Data
public class BatchTransferPreviewVO {

    private Long targetAreaId;

    private String targetAreaName;

    private Integer selectedCount;

    private Integer moveCount;

    private Integer invalidCount;

    private Integer alreadyInTargetCount;

    /** 被进行中占座批次占住（含超时未到）而无法调区的资产数 */
    private Integer seatHoldingCount;

    private Boolean canSubmit;

    private List<BatchTransferItemVO> items;
}
