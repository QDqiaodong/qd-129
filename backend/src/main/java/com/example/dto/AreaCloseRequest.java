package com.example.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 值班员为阅览区挂“今日闭馆”牌：必须给出闭馆结束时刻，
 * 结束时刻前高峰占座开批会被拦截，到期自动失效，无需提前摘牌
 */
@Data
public class AreaCloseRequest {

    /** 闭馆结束时刻（必须晚于当前时间） */
    private LocalDateTime closedUntil;
}
