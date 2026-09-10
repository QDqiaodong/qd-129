package com.example.service;

import com.example.TestRedisConfig;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.entity.AreaChangeLog;
import com.example.entity.DeskChair;
import com.example.entity.ReadingArea;
import com.example.mapper.AreaChangeLogMapper;
import com.example.mapper.DeskChairMapper;
import com.example.mapper.ReadingAreaMapper;
import com.example.mapper.TagMapper;
import com.example.vo.AreaCapacityDetailVO;
import com.example.vo.AreaCapacityStatVO;
import com.example.vo.AreaChangeTrendVO;
import com.example.vo.AreaTagStatVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = "spring.sql.init.mode=always")
@Import(TestRedisConfig.class)
class DashboardServiceIntegrationTest {

    @Autowired
    private DashboardService dashboardService;

    @Autowired
    private ReadingAreaMapper readingAreaMapper;

    @Autowired
    private DeskChairMapper deskChairMapper;

    @Autowired
    private TagMapper tagMapper;

    @Autowired
    private AreaChangeLogMapper areaChangeLogMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long area1;
    private Long area2;
    private Long emptyArea;
    private Long disabledArea;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("DELETE FROM area_change_log_00");
        jdbcTemplate.execute("DELETE FROM area_change_log_01");
        jdbcTemplate.execute("DELETE FROM desk_chair_tag");
        jdbcTemplate.execute("DELETE FROM desk_chair");
        jdbcTemplate.execute("DELETE FROM tag");
        jdbcTemplate.execute("DELETE FROM reading_area");

        area1 = createArea("A001", "第一阅览区", 1);
        area2 = createArea("A002", "第二阅览区", 1);
        emptyArea = createArea("A003", "空阅览区", 1);
        disabledArea = createArea("A004", "停用阅览区", 0);

        Long tagStudy = createTag("TAG001", "自习专用");
        Long tagDouble = createTag("TAG002", "双人桌");

        // 分区1：两张可用（容量 2、4），一张停用（容量 1），一张已删除（容量 2）
        Long d1 = createDeskChair("DC001", area1, 2, 1);
        Long d2 = createDeskChair("DC002", area1, 4, 1);
        createDeskChair("DC003", area1, 1, 0);
        createDeskChair("DC004", area1, 2, -1);
        bindTag(d1, tagStudy);
        bindTag(d1, tagDouble);
        bindTag(d2, tagStudy);

        // 分区2：一张可用（容量 1）
        Long d5 = createDeskChair("DC005", area2, 1, 1);
        bindTag(d5, tagStudy);
    }

    private Long createArea(String code, String name, int status) {
        ReadingArea area = new ReadingArea();
        area.setAreaCode(code);
        area.setAreaName(name);
        area.setStatus(status);
        readingAreaMapper.insert(area);
        return area.getId();
    }

    private Long createTag(String code, String name) {
        Tag tag = new Tag();
        tag.setTagCode(code);
        tag.setTagName(name);
        tag.setTagColor("#409EFF");
        tag.setStatus(1);
        tagMapper.insert(tag);
        return tag.getId();
    }

    private Long createDeskChair(String assetCode, Long areaId, int capacity, int status) {
        DeskChair dc = new DeskChair();
        dc.setAssetCode(assetCode);
        dc.setCapacity(capacity);
        dc.setAreaId(areaId);
        dc.setDimensions("120x60x75cm");
        dc.setStatus(status);
        deskChairMapper.insert(dc);
        return dc.getId();
    }

    private void bindTag(Long deskChairId, Long tagId) {
        jdbcTemplate.update("INSERT INTO desk_chair_tag (desk_chair_id, tag_id) VALUES (?, ?)",
                deskChairId, tagId);
    }

    private AreaChangeLog writeLog(Long deskChairId, Long oldAreaId, Long newAreaId, LocalDateTime createdAt) {
        AreaChangeLog log = new AreaChangeLog();
        log.setDeskChairId(deskChairId);
        log.setOldAreaId(oldAreaId);
        log.setNewAreaId(newAreaId);
        log.setChangeReason("运营调整");
        log.setOperator("测试员");
        String tableIndex = deskChairId % 2 == 0 ? "00" : "01";
        areaChangeLogMapper.insertLog(log, tableIndex);
        if (createdAt != null) {
            jdbcTemplate.update("UPDATE area_change_log_" + tableIndex
                    + " SET created_at = ? WHERE id = ?", createdAt, log.getId());
        }
        return log;
    }

    private Map<Long, AreaCapacityStatVO> statsById(List<AreaCapacityStatVO> stats) {
        return stats.stream().collect(Collectors.toMap(AreaCapacityStatVO::getAreaId, s -> s));
    }

    @Test
    void statsShouldCountAvailableDisabledAndCapacityExcludingDeleted() {
        List<AreaCapacityStatVO> stats = dashboardService.getAreaCapacityStats();
        assertEquals(4, stats.size());

        AreaCapacityStatVO s1 = statsById(stats).get(area1);
        assertEquals(3, s1.getTotalCount());
        assertEquals(2, s1.getAvailableCount());
        assertEquals(1, s1.getDisabledCount());
        assertEquals(6, s1.getTotalCapacity());

        AreaCapacityStatVO s2 = statsById(stats).get(area2);
        assertEquals(1, s2.getTotalCount());
        assertEquals(1, s2.getAvailableCount());
        assertEquals(0, s2.getDisabledCount());
        assertEquals(1, s2.getTotalCapacity());

        // 空分区与停用分区都显示 0
        AreaCapacityStatVO empty = statsById(stats).get(emptyArea);
        assertEquals(0, empty.getTotalCount());
        assertEquals(0, empty.getAvailableCount());
        assertEquals(0, empty.getDisabledCount());
        assertEquals(0, empty.getTotalCapacity());

        AreaCapacityStatVO disabled = statsById(stats).get(disabledArea);
        assertEquals(0, disabled.getAreaStatus());
        assertEquals(0, disabled.getTotalCount());
        assertEquals(0, disabled.getTotalCapacity());
    }

    @Test
    void statsShouldIncludeTagComposition() {
        AreaCapacityStatVO s1 = statsById(dashboardService.getAreaCapacityStats()).get(area1);
        List<AreaTagStatVO> tags = s1.getTagStats();
        assertEquals(2, tags.size());
        AreaTagStatVO study = tags.stream().filter(t -> "自习专用".equals(t.getTagName())).findFirst().orElseThrow();
        assertEquals(2, study.getDeskChairCount());
        AreaTagStatVO doubleDesk = tags.stream().filter(t -> "双人桌".equals(t.getTagName())).findFirst().orElseThrow();
        assertEquals(1, doubleDesk.getDeskChairCount());

        AreaCapacityStatVO empty = statsById(dashboardService.getAreaCapacityStats()).get(emptyArea);
        assertTrue(empty.getTagStats().isEmpty());
    }

    @Test
    void detailShouldListDesksTagsAndRecentChanges() {
        // 两条最近变更：一条迁出 area1，一条迁入 area1，分布在不同分片表
        List<DeskChair> area1Desks = deskChairMapper.findByAreaId(area1);
        List<DeskChair> area2Desks = deskChairMapper.findByAreaId(area2);
        writeLog(area1Desks.get(0).getId(), area1, area2, LocalDateTime.now().minusDays(1));
        writeLog(area2Desks.get(0).getId(), area2, area1, LocalDateTime.now().minusHours(2));

        AreaCapacityDetailVO detail = dashboardService.getAreaCapacityDetail(area1, 10);
        assertEquals("第一阅览区", detail.getAreaName());
        assertEquals(3, detail.getTotalCount());
        assertEquals(2, detail.getAvailableCount());
        assertEquals(1, detail.getDisabledCount());
        assertEquals(6, detail.getTotalCapacity());
        assertEquals(3, detail.getDeskChairs().size());
        assertTrue(detail.getDeskChairs().stream().noneMatch(d -> "DC004".equals(d.getAssetCode())));
        // 桌椅带标签
        DeskChair dc001 = detail.getDeskChairs().stream()
                .filter(d -> "DC001".equals(d.getAssetCode())).findFirst().orElseThrow();
        assertEquals(2, dc001.getTags().size());
        // 标签构成
        assertEquals(2, detail.getTagStats().size());
        // 最近变更（含迁出和迁入）
        assertEquals(2, detail.getRecentChanges().size());
        assertTrue(detail.getRecentChanges().stream().allMatch(log ->
                area1.equals(log.getOldAreaId()) || area1.equals(log.getNewAreaId())));
    }

    @Test
    void detailOfUnknownAreaShouldReject() {
        assertThrows(IllegalArgumentException.class,
                () -> dashboardService.getAreaCapacityDetail(99999L, 10));
    }

    @Test
    void trendShouldAggregateByDateAcrossShardsAndExcludeDeletedAssets() {
        DeskChair activeInArea1 = deskChairMapper.findByAreaId(area1).get(0);
        Long deletedId = deskChairMapper.selectList(
                new LambdaQueryWrapper<DeskChair>()
                        .eq(DeskChair::getAssetCode, "DC004")).get(0).getId();

        LocalDate today = LocalDate.now();
        writeLog(activeInArea1.getId(), area1, area2, today.minusDays(2).atTime(9, 0));
        writeLog(activeInArea1.getId(), area2, area1, today.minusDays(2).atTime(15, 0));
        writeLog(activeInArea1.getId(), area1, area2, today.atTime(10, 0));
        // 已删除资产的变更不计入趋势
        writeLog(deletedId, area1, area2, today.minusDays(1).atTime(10, 0));

        LocalDate start = today.minusDays(4);
        List<AreaChangeTrendVO> trend = dashboardService.getChangeTrend(start, today, null);
        assertEquals(5, trend.size());
        Map<String, Integer> byDate = trend.stream()
                .collect(Collectors.toMap(AreaChangeTrendVO::getDate, AreaChangeTrendVO::getChangeCount));
        assertEquals(0, byDate.get(start.toString()));
        assertEquals(2, byDate.get(today.minusDays(2).toString()));
        assertEquals(0, byDate.get(today.minusDays(1).toString()));
        assertEquals(1, byDate.get(today.toString()));
    }

    @Test
    void trendShouldFilterByAreaAndRejectBadRange() {
        DeskChair d1 = deskChairMapper.findByAreaId(area1).get(0);
        DeskChair d2 = deskChairMapper.findByAreaId(area2).get(0);
        LocalDate today = LocalDate.now();
        writeLog(d1.getId(), area1, area2, today.atTime(9, 0));
        writeLog(d2.getId(), area2, area1, today.atTime(11, 0));

        List<AreaChangeTrendVO> area1Trend =
                dashboardService.getChangeTrend(today, today, area1);
        // 两条变更都涉及 area1（一条迁出、一条迁入）
        assertEquals(2, area1Trend.get(0).getChangeCount());

        assertThrows(IllegalArgumentException.class,
                () -> dashboardService.getChangeTrend(today, today.minusDays(1), null));
        assertThrows(IllegalArgumentException.class,
                () -> dashboardService.getChangeTrend(today.minusDays(400), today, null));
    }
}
