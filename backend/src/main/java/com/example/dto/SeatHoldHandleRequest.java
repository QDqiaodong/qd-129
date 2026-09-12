package com.example.dto;

import lombok.Data;

@Data
public class SeatHoldHandleRequest {

    /** 操作值班员（释放 / 标记超时未到 / 整批结束） */
    private String operator;
}
