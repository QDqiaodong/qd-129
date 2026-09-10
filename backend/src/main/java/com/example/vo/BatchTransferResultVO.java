package com.example.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class BatchTransferResultVO {

    private Long batchId;

    private String batchNo;

    private String status;

    private Long targetAreaId;

    private String targetAreaName;

    private Integer totalCount;

    private Integer successCount;

    private Integer failCount;

    private String changeReason;

    private String operator;

    private String errorMessage;

    private LocalDateTime createdAt;

    private List<BatchTransferItemVO> items;
}
