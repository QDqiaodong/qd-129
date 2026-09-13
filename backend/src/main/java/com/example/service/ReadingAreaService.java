package com.example.service;

import com.example.entity.ReadingArea;

import java.time.LocalDateTime;
import java.util.List;

public interface ReadingAreaService {

    List<ReadingArea> findAll();

    ReadingArea findById(Long id);

    ReadingArea findByCode(String areaCode);

    ReadingArea save(ReadingArea readingArea);

    ReadingArea update(ReadingArea readingArea);

    void deleteById(Long id);

    /** 挂“今日闭馆”牌：登记闭馆结束时刻，到期自动失效 */
    ReadingArea markClosed(Long id, LocalDateTime closedUntil);

    /** 提前摘牌：清空闭馆结束时刻，分区即刻恢复可开占座 */
    ReadingArea clearClosed(Long id);
}