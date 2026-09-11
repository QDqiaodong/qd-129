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
     * 不可迁移原因码：NOT_FOUND（资产已删除）/ DISABLED（资产已停用）/ ALREADY_IN_TARGET（已在目标分区）
     */
    private String reasonCode;
}
