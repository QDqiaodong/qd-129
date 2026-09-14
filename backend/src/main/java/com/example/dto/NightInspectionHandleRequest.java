package com.example.dto;

import lombok.Data;

@Data
public class NightInspectionHandleRequest {

    /** 结束批次时的补充说明（可选，记入批次备注留痕） */
    private String handleOpinion;

    /** 操作人 */
    private String operator;
}
