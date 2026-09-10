package com.example.service;

import com.example.vo.AreaCapacityDetailVO;
import com.example.vo.AreaCapacityStatVO;
import com.example.vo.AreaChangeTrendVO;

import java.time.LocalDate;
import java.util.List;

public interface DashboardService {

    /** 全部分区的容量运营统计（排除已删除资产，空分区各项为 0） */
    List<AreaCapacityStatVO> getAreaCapacityStats();

    /** 单个分区的下钻明细：桌椅列表、标签构成、最近调区变更 */
    AreaCapacityDetailVO getAreaCapacityDetail(Long areaId, int recentLimit);

    /**
     * 按日期统计调区变更趋势，区间闭开 [start, end+1天)。
     * 无变更的日期补 0；areaId 为空时统计全馆。
     */
    List<AreaChangeTrendVO> getChangeTrend(LocalDate start, LocalDate end, Long areaId);
}
