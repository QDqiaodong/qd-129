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

    /**
     * 挂“今晚临时加座”：登记增加的座位数与失效时刻；
     * 看板容纳人数立即加上该名额，失效时刻一过自动回到档案容量
     */
    ReadingArea markExtraSeats(Long id, Integer extraSeatCount, LocalDateTime extraSeatUntil);

    /** 提前撤销临时加座：清空名额与失效时刻，看板即刻回到档案容量 */
    ReadingArea clearExtraSeats(Long id);
}