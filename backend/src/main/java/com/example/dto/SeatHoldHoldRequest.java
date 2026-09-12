package com.example.dto;

import lombok.Data;

import java.util.List;

@Data
public class SeatHoldHoldRequest {

    /** 追加占住的桌椅档案资产 ID 列表 */
    private List<Long> deskChairIds;

    /** 操作值班员 */
    private String operator;
}
