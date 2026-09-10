package com.example.controller;

import com.example.service.DashboardService;
import com.example.vo.AreaCapacityDetailVO;
import com.example.vo.AreaCapacityStatVO;
import com.example.vo.AreaChangeTrendVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/dashboard")
@CrossOrigin(origins = "*")
public class DashboardController {

    @Autowired
    private DashboardService dashboardService;

    /** 分区容量运营看板：桌椅总数、可用/停用、容纳人数 */
    @GetMapping("/area-capacity")
    public ResponseEntity<List<AreaCapacityStatVO>> getAreaCapacityStats() {
        return ResponseEntity.ok(dashboardService.getAreaCapacityStats());
    }

    /** 分区下钻：桌椅明细 + 标签构成 + 最近调区记录 */
    @GetMapping("/area-capacity/{areaId}")
    public ResponseEntity<AreaCapacityDetailVO> getAreaCapacityDetail(
            @PathVariable Long areaId,
            @RequestParam(required = false, defaultValue = "10") Integer recentLimit) {
        int limit = recentLimit == null ? 10 : Math.min(Math.max(recentLimit, 1), 50);
        return ResponseEntity.ok(dashboardService.getAreaCapacityDetail(areaId, limit));
    }

    /** 按日期查看调区变更趋势；可选分区过滤，默认最近 30 天 */
    @GetMapping("/change-trend")
    public ResponseEntity<List<AreaChangeTrendVO>> getChangeTrend(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) Long areaId) {
        LocalDate end = endDate == null || endDate.isBlank() ? LocalDate.now() : LocalDate.parse(endDate);
        LocalDate start = startDate == null || startDate.isBlank() ? end.minusDays(29) : LocalDate.parse(startDate);
        return ResponseEntity.ok(dashboardService.getChangeTrend(start, end, areaId));
    }
}
