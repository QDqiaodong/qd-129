package com.example.dto;

import lombok.Data;

@Data
public class StocktakeHandleRequest {

    /** 处理意见（确认差异时必填） */
    private String handleOpinion;

    /** 操作人 */
    private String operator;
}
