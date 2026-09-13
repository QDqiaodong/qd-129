package com.example.dto;

import lombok.Data;

@Data
public class StocktakeHandleRequest {

    /** 处理意见（确认非缺失差异时必填） */
    private String handleOpinion;

    /** 缺失原因（确认 MISSING 缺失项时必填，批次完成前所有缺失项都要写明） */
    private String missingReason;

    /** 操作人 */
    private String operator;
}
