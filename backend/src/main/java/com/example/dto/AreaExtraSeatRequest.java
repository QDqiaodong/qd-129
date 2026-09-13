package com.example.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 值班员为阅览区挂“今晚临时加座”：必须给出增加的座位数（正整数）和失效时刻（晚于当前时间）。
 * 挂上后容量看板容纳人数立即加上这笔临时名额，高峰占座开批/追加按加座后的人数校验；
 * 失效时刻一过自动回到档案容量，同分区不再享受临时名额
 */
@Data
public class AreaExtraSeatRequest {

    /** 增加的座位数（必须为正整数） */
    private Integer extraSeatCount;

    /** 临时加座失效时刻（必须晚于当前时间） */
    private LocalDateTime extraSeatUntil;
}
