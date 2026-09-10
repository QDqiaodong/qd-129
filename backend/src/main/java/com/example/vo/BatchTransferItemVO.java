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
}
