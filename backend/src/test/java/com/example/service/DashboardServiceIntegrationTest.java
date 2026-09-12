package com.example.service;

import com.example.TestRedisConfig;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.dto.LostItemClaimRequest;
import com.example.dto.LostItemCreateRequest;
import com.example.dto.SeatHoldCreateRequest;
import com.example.dto.SeatHoldHandleRequest;
import com.example.entity.AreaChangeLog;
import com.example.entity.DeskChair;
import com.example.entity.LostItem;
import com.example.entity.ReadingArea;
import com.example.entity.SeatHoldBatch;
import com.example.entity.Tag;
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
    private SeatHoldService seatHoldService;

    @Autowired
    private LostItemService lostItemService;

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
        jdbcTemplate.execute("DELETE FROM lost_item");
        jdbcTemplate.execute("DELETE FROM seat_hold_item");
        jdbcTemplate.execute("DELETE FROM seat_hold_batch");
        jdbcTemplate.execute("DELETE FROM repair_order");
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

    private SeatHoldCreateRequest holdRequest(Long areaId, List<String> assetCodes) {
        List<Long> ids = assetCodes.stream()
                .map(code -> deskChairMapper.findByAssetCode(code).getId())
                .toList();
        SeatHoldCreateRequest req = new SeatHoldCreateRequest();
        req.setAreaId(areaId);
        req.setTimeSlot("08:00-11:30 早高峰");
        req.setDeskChairIds(ids);
        req.setOperator("值班员甲");
        return req;
    }

    private SeatHoldHandleRequest handle(String operator) {
        SeatHoldHandleRequest req = new SeatHoldHandleRequest();
        req.setOperator(operator);
        return req;
    }

    private LostItemCreateRequest lostRequest(Long areaId, String assetCode, String itemName) {
        LostItemCreateRequest req = new LostItemCreateRequest();
        req.setAreaId(areaId);
        req.setDeskChairId(deskChairMapper.findByAssetCode(assetCode).getId());
        req.setItemName(itemName);
        req.setStorageLocation("服务台抽屉 1 号");
        req.setOperator("值班员甲");
        return req;
    }

    private LostItemClaimRequest claimRequest() {
        LostItemClaimRequest req = new LostItemClaimRequest();
        req.setClaimerName("张三");
        req.setClaimerVerify("学生证 20230101，核对一致");
        req.setClaimConclusion("核验通过，物品完好交还领取人");
        req.setOperator("值班员乙");
        return req;
    }

    @Test
    void statsShouldSeparateSeatHoldOccupiedFromDisabled() {
        // 高峰占座：DC001 在占（area1），DC005 标记超时未到（area2）
        SeatHoldBatch batch1 = seatHoldService.createBatch(holdRequest(area1, List.of("DC001")));
        SeatHoldBatch batch2 = seatHoldService.createBatch(holdRequest(area2, List.of("DC005")));
        seatHoldService.markTimeout(batch2.getId(), batch2.getItems().get(0).getId(), handle("值班员乙"));

        Map<Long, AreaCapacityStatVO> stats = statsById(dashboardService.getAreaCapacityStats());

        // area1：在占的 DC001 单独计入占座占用，不再混入停用；档案停用的 DC003 仍在停用
        AreaCapacityStatVO s1 = stats.get(area1);
        assertEquals(3, s1.getTotalCount());
        assertEquals(1, s1.getAvailableCount());
        assertEquals(1, s1.getOccupiedCount());
        assertEquals(1, s1.getDisabledCount());
        assertEquals(4, s1.getTotalCapacity());
        assertEquals(2, s1.getOccupiedCapacity());
        assertEquals(s1.getTotalCount(),
                s1.getAvailableCount() + s1.getOccupiedCount() + s1.getDisabledCount());

        // area2：超时未到仍属进行中占座占用
        AreaCapacityStatVO s2 = stats.get(area2);
        assertEquals(1, s2.getTotalCount());
        assertEquals(0, s2.getAvailableCount());
        assertEquals(1, s2.getOccupiedCount());
        assertEquals(0, s2.getDisabledCount());
        assertEquals(1, s2.getOccupiedCapacity());

        // 看板占用数与占座批次的在占/超时未到数对得上
        SeatHoldBatch refreshed1 = seatHoldService.findById(batch1.getId());
        SeatHoldBatch refreshed2 = seatHoldService.findById(batch2.getId());
        assertEquals(refreshed1.getHeldCount() + refreshed1.getTimeoutCount(), s1.getOccupiedCount().longValue());
        assertEquals(refreshed2.getHeldCount() + refreshed2.getTimeoutCount(), s2.getOccupiedCount().longValue());

        // 下钻明细：逐椅占用标记与桌椅实时状态对得上（在占桌椅状态为停用，但单独标为占座占用）
        AreaCapacityDetailVO detail = dashboardService.getAreaCapacityDetail(area1, 10);
        assertEquals(1, detail.getOccupiedCount());
        assertEquals(2, detail.getOccupiedCapacity());
        assertEquals(detail.getTotalCount(),
                detail.getAvailableCount() + detail.getOccupiedCount() + detail.getDisabledCount());
        DeskChair dc001 = detail.getDeskChairs().stream()
                .filter(d -> "DC001".equals(d.getAssetCode())).findFirst().orElseThrow();
        assertEquals(Boolean.TRUE, dc001.getOccupied());
        assertEquals(0, dc001.getStatus());
        DeskChair dc003 = detail.getDeskChairs().stream()
                .filter(d -> "DC003".equals(d.getAssetCode())).findFirst().orElseThrow();
        assertEquals(Boolean.FALSE, dc003.getOccupied());

        // 释放后刷新：占用回到可用，三桶与桌椅状态重新对齐
        seatHoldService.release(batch1.getId(), batch1.getItems().get(0).getId(), handle("值班员甲"));
        AreaCapacityStatVO after = statsById(dashboardService.getAreaCapacityStats()).get(area1);
        assertEquals(2, after.getAvailableCount());
        assertEquals(0, after.getOccupiedCount());
        assertEquals(1, after.getDisabledCount());
        assertEquals(6, after.getTotalCapacity());
        assertEquals(0, after.getOccupiedCapacity());
    }

    @Test
    void statsShouldCountAvailableDisabledAndCapacityExcludingDeleted() {
        List<AreaCapacityStatVO> stats = dashboardService.getAreaCapacityStats();
        assertEquals(4, stats.size());

        AreaCapacityStatVO s1 = statsById(stats).get(area1);
        assertEquals(3, s1.getTotalCount());
        assertEquals(2, s1.getAvailableCount());
        assertEquals(0, s1.getOccupiedCount());
        assertEquals(1, s1.getDisabledCount());
        assertEquals(6, s1.getTotalCapacity());
        assertEquals(0, s1.getOccupiedCapacity());

        AreaCapacityStatVO s2 = statsById(stats).get(area2);
        assertEquals(1, s2.getTotalCount());
        assertEquals(1, s2.getAvailableCount());
        assertEquals(0, s2.getOccupiedCount());
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
        assertEquals(0, detail.getOccupiedCount());
        assertEquals(1, detail.getDisabledCount());
        assertEquals(6, detail.getTotalCapacity());
        assertEquals(0, detail.getOccupiedCapacity());
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
    void statsAndDetailShouldShowPendingLostItemsExcludingClaimed() {
        // area1：两单待领、一单已领取闭环；area2：一单待领
        LostItem pending1 = lostItemService.create(lostRequest(area1, "DC001", "黑色双肩包"));
        LostItem pending2 = lostItemService.create(lostRequest(area1, "DC002", "保温杯"));
        LostItem claimed = lostItemService.create(lostRequest(area1, "DC003", "雨伞"));
        lostItemService.claim(claimed.getId(), claimRequest());
        lostItemService.create(lostRequest(area2, "DC005", "校园卡"));

        // 看板件数只算待领取，已领取闭环不计入；空分区与停用分区为 0
        Map<Long, AreaCapacityStatVO> stats = statsById(dashboardService.getAreaCapacityStats());
        assertEquals(2, stats.get(area1).getPendingLostCount());
        assertEquals(1, stats.get(area2).getPendingLostCount());
        assertEquals(0, stats.get(emptyArea).getPendingLostCount());
        assertEquals(0, stats.get(disabledArea).getPendingLostCount());

        // 下钻抽屉：待领单号、物品名称、桌椅编号齐全，已领取单不在清单里
        AreaCapacityDetailVO detail = dashboardService.getAreaCapacityDetail(area1, 10);
        assertEquals(2, detail.getPendingLostCount());
        assertEquals(2, detail.getPendingLostItems().size());
        assertTrue(detail.getPendingLostItems().stream()
                .allMatch(item -> LostItem.STATUS_PENDING.equals(item.getStatus())));
        assertTrue(detail.getPendingLostItems().stream()
                .noneMatch(item -> claimed.getItemNo().equals(item.getItemNo())));
        LostItem first = detail.getPendingLostItems().stream()
                .filter(item -> pending1.getItemNo().equals(item.getItemNo()))
                .findFirst().orElseThrow();
        assertEquals("黑色双肩包", first.getItemName());
        assertEquals("DC001", first.getAssetCode());

        // 再领取一单后重新查询（刷新）：件数与清单同步减少，剩余待领仍在
        lostItemService.claim(pending2.getId(), claimRequest());
        Map<Long, AreaCapacityStatVO> reloaded = statsById(dashboardService.getAreaCapacityStats());
        assertEquals(1, reloaded.get(area1).getPendingLostCount());
        AreaCapacityDetailVO reloadedDetail = dashboardService.getAreaCapacityDetail(area1, 10);
        assertEquals(1, reloadedDetail.getPendingLostCount());
        assertEquals(1, reloadedDetail.getPendingLostItems().size());
        assertEquals(pending1.getItemNo(), reloadedDetail.getPendingLostItems().get(0).getItemNo());
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
