package com.example.dto;

import lombok.Data;

import java.util.List;

@Data
public class BatchTransferRequest {

    private List<Long> deskChairIds;

    private Long targetAreaId;

    private String changeReason;

    private String operator;
}
