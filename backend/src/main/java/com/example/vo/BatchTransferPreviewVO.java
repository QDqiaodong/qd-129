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

    private Boolean canSubmit;

    private List<BatchTransferItemVO> items;
}
