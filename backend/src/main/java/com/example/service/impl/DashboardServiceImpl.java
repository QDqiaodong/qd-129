package com.example.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.entity.DeskChair;
import com.example.entity.ReadingArea;
import com.example.mapper.DashboardMapper;
import com.example.mapper.DeskChairMapper;
import com.example.mapper.ReadingAreaMapper;
import com.example.mapper.TagMapper;
import com.example.service.AreaChangeLogService;
import com.example.service.DashboardService;
import com.example.vo.AreaCapacityDetailVO;
import com.example.vo.AreaCapacityStatVO;
import com.example.vo.AreaChangeTrendVO;
import com.example.vo.AreaTagStatVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class DashboardServiceImpl implements DashboardService {

    /** 趋势查询最长跨度，避免一次拉取过多数据 */
    private static final long MAX_TREND_DAYS = 366;

    @Autowired
    private DashboardMapper dashboardMapper;

    @Autowired
    private ReadingAreaMapper readingAreaMapper;

    @Autowired
    private DeskChairMapper deskChairMapper;

    @Autowired
    private TagMapper tagMapper;

    @Autowired
    private AreaChangeLogService areaChangeLogService;

    @Override
    public List<AreaCapacityStatVO> getAreaCapacityStats() {
        List<AreaCapacityStatVO> stats = dashboardMapper.findAreaCapacityStats();

        // 标签构成一次查全量再按分区分组，避免逐分区查询
        Map<Long, List<AreaTagStatVO>> tagStatsByArea = new HashMap<>();
        for (AreaTagStatVO tagStat : dashboardMapper.findAllTagStats()) {
            tagStatsByArea.computeIfAbsent(tagStat.getAreaId(), k -> new ArrayList<>()).add(tagStat);
        }
        for (AreaCapacityStatVO stat : stats) {
            stat.setTagStats(tagStatsByArea.getOrDefault(stat.getAreaId(), new ArrayList<>()));
        }
        return stats;
    }

    @Override
    public AreaCapacityDetailVO getAreaCapacityDetail(Long areaId, int recentLimit) {
        ReadingArea area = readingAreaMapper.selectById(areaId);
        if (area == null) {
            throw new IllegalArgumentException("分区不存在或已删除");
        }

        AreaCapacityDetailVO detail = new AreaCapacityDetailVO();
        detail.setAreaId(area.getId());
        detail.setAreaCode(area.getAreaCode());
        detail.setAreaName(area.getAreaName());
        detail.setAreaStatus(area.getStatus());
        detail.setDescription(area.getDescription());

        // 桌椅明细：含停用资产、排除已删除资产，并补齐标签
        List<DeskChair> deskChairs = deskChairMapper.selectList(
                new LambdaQueryWrapper<DeskChair>()
                        .eq(DeskChair::getAreaId, areaId)
                        .ge(DeskChair::getStatus, 0)
                        .orderByAsc(DeskChair::getAssetCode));
        int total = deskChairs.size();
        int available = 0;
        int disabled = 0;
        int totalCapacity = 0;
        for (DeskChair deskChair : deskChairs) {
            deskChair.setAreaName(area.getAreaName());
            deskChair.setTags(tagMapper.findByDeskChairId(deskChair.getId()));
            if (deskChair.getStatus() != null && deskChair.getStatus() == 1) {
                available++;
                totalCapacity += deskChair.getCapacity() == null ? 0 : deskChair.getCapacity();
            } else {
                disabled++;
            }
        }
        detail.setDeskChairs(deskChairs);
        detail.setTotalCount(total);
        detail.setAvailableCount(available);
        detail.setDisabledCount(disabled);
        detail.setTotalCapacity(totalCapacity);

        detail.setTagStats(dashboardMapper.findTagStatsByAreaId(areaId));
        detail.setRecentChanges(areaChangeLogService.findRecentByAreaId(areaId,
                recentLimit > 0 ? recentLimit : 10));
        return detail;
    }

    @Override
    public List<AreaChangeTrendVO> getChangeTrend(LocalDate start, LocalDate end, Long areaId) {
        if (start == null || end == null) {
            throw new IllegalArgumentException("开始日期和结束日期不能为空");
        }
        if (end.isBefore(start)) {
            throw new IllegalArgumentException("结束日期不能早于开始日期");
        }
        long days = ChronoUnit.DAYS.between(start, end) + 1;
        if (days > MAX_TREND_DAYS) {
            throw new IllegalArgumentException("查询区间不能超过 " + MAX_TREND_DAYS + " 天");
        }

        LocalDateTime startTime = start.atStartOfDay();
        LocalDateTime endTime = end.plusDays(1).atStartOfDay();

        List<LocalDateTime> allTimes = new ArrayList<>();
        allTimes.addAll(dashboardMapper.findChangeTimesFromTable00(startTime, endTime, areaId));
        allTimes.addAll(dashboardMapper.findChangeTimesFromTable01(startTime, endTime, areaId));

        Map<LocalDate, Integer> counts = new HashMap<>();
        for (LocalDateTime time : allTimes) {
            if (time != null) {
                counts.merge(time.toLocalDate(), 1, Integer::sum);
            }
        }

        // 按日期补齐无变更的日期，保证前端图表连续
        List<AreaChangeTrendVO> trend = new ArrayList<>();
        for (long i = 0; i < days; i++) {
            LocalDate date = start.plusDays(i);
            AreaChangeTrendVO vo = new AreaChangeTrendVO();
            vo.setDate(date.toString());
            vo.setChangeCount(counts.getOrDefault(date, 0));
            trend.add(vo);
        }
        return trend;
    }
}
