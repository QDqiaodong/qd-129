package com.example.service;

import com.example.entity.AreaChangeLog;

import java.util.List;

public interface AreaChangeLogService {

    List<AreaChangeLog> findAll();

    List<AreaChangeLog> findByDeskChairId(Long deskChairId);

    /** 查入/迁出该分区的最近调区记录（已排除已删除资产），按时间倒序取前 limit 条 */
    List<AreaChangeLog> findRecentByAreaId(Long areaId, int limit);
}