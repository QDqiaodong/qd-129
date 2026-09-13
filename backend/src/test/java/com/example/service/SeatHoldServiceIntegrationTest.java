package com.example.service;

import com.example.TestRedisConfig;
import com.example.dto.RepairOrderCreateRequest;
import com.example.dto.SeatHoldCreateRequest;
import com.example.dto.SeatHoldHandleRequest;
import com.example.dto.SeatHoldHoldRequest;
import com.example.entity.DeskChair;
import com.example.entity.ReadingArea;
import com.example.entity.SeatHoldBatch;
import com.example.entity.SeatHoldItem;
import com.example.mapper.DeskChairMapper;
import com.example.mapper.ReadingAreaMapper;
import com.example.vo.SeatHoldClearingItemVO;
import com.example.vo.SeatHoldClearingVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = "spring.sql.init.mode=always")
@Import(TestRedisConfig.class)
class SeatHoldServiceIntegrationTest {

    @Autowired
    private SeatHoldService seatHoldService;

    @Autowired
    private ReadingAreaService readingAreaService;

    @Autowired
    private RepairOrderService repairOrderService;

    @Autowired
    private DeskChairMapper deskChairMapper;

    @Autowired
    private ReadingAreaMapper readingAreaMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long area1;
    private Long area2;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("DELETE FROM seat_hold_item");
        jdbcTemplate.execute("DELETE FROM seat_hold_batch");
        jdbcTemplate.execute("DELETE FROM repair_order");
        jdbcTemplate.execute("DELETE FROM desk_chair_tag");
        jdbcTemplate.execute("DELETE FROM desk_chair");
        jdbcTemplate.execute("DELETE FROM reading_area");

        area1 = createArea("A001", "第一阅览区");
        area2 = createArea("A002", "第二阅览区");

        createDeskChair("DC001", area1, 1);
        createDeskChair("DC002", area1, 1);
        createDeskChair("DC003", area1, 0);
        createDeskChair("DC004", area2, 1);
    }

    private Long createArea(String code, String name) {
        ReadingArea area = new ReadingArea();
        area.setAreaCode(code);
        area.setAreaName(name);
        area.setStatus(1);
        readingAreaMapper.insert(area);
        return area.getId();
    }

    private void createDeskChair(String assetCode, Long areaId, int status) {
        DeskChair dc = new DeskChair();
        dc.setAssetCode(assetCode);
        dc.setCapacity(2);
        dc.setAreaId(areaId);
        dc.setDimensions("120x60x75cm");
        dc.setStatus(status);
        deskChairMapper.insert(dc);
    }

    private Long deskId(String code) {
        return deskChairMapper.findByAssetCode(code).getId();
    }

    private int deskStatus(String code) {
        return deskChairMapper.findByAssetCode(code).getStatus();
    }

    private SeatHoldCreateRequest createRequest(Long areaId, List<Long> ids, String operator) {
        SeatHoldCreateRequest req = new SeatHoldCreateRequest();
        req.setAreaId(areaId);
        req.setTimeSlot("08:00-11:30 早高峰");
        req.setDeskChairIds(ids);
        req.setRemark("期末高峰占座");
        req.setOperator(operator);
        return req;
    }

    private SeatHoldHandleRequest handle(String operator) {
        SeatHoldHandleRequest req = new SeatHoldHandleRequest();
        req.setOperator(operator);
        return req;
    }

    private SeatHoldHoldRequest holdRequest(List<Long> ids, String operator) {
        SeatHoldHoldRequest req = new SeatHoldHoldRequest();
        req.setDeskChairIds(ids);
        req.setOperator(operator);
        return req;
    }

    private SeatHoldItem itemOf(SeatHoldBatch batch, String code) {
        return batch.getItems().stream()
                .filter(i -> code.equals(i.getAssetCode()))
                .findFirst().orElseThrow();
    }

    /** 对指定资产建一张未闭环报修单（报修即锁定，桌椅置停用） */
    private void createOpenRepair(String assetCode, Long areaId) {
        RepairOrderCreateRequest req = new RepairOrderCreateRequest();
        req.setDeskChairId(deskId(assetCode));
        req.setAreaId(areaId);
        req.setDamagePart("桌腿");
        req.setPhenomenon("晃动无法正常使用");
        req.setReporter("读者");
        repairOrderService.create(req);
    }

    @Test
    void createShouldHoldAssetsAndSetThemDisabled() {
        SeatHoldBatch batch = seatHoldService.createBatch(
                createRequest(area1, List.of(deskId("DC001"), deskId("DC002")), "值班员甲"));

        assertEquals(SeatHoldBatch.STATUS_OPEN, batch.getStatus());
        assertEquals(2, batch.getTotalCount());
        assertEquals(2, batch.getHeldCount());
        assertEquals(0, batch.getTimeoutCount());
        assertEquals(0, batch.getReleasedCount());
        assertEquals("第一阅览区", batch.getAreaName());
        assertEquals(0, deskStatus("DC001"));
        assertEquals(0, deskStatus("DC002"));
        SeatHoldItem item = itemOf(batch, "DC001");
        assertEquals(SeatHoldItem.STATUS_HOLDING, item.getItemStatus());
        assertEquals(1, item.getPreviousDeskStatus());
        // 详情联表带出桌椅实时状态
        assertEquals(0, item.getDeskStatus());
    }

    @Test
    void createShouldRejectMissingAreaSlotOperatorOrEmptyAssets() {
        assertThrows(IllegalArgumentException.class,
                () -> seatHoldService.createBatch(createRequest(null, List.of(deskId("DC001")), "甲")));
        SeatHoldCreateRequest noSlot = createRequest(area1, List.of(deskId("DC001")), "甲");
        noSlot.setTimeSlot("  ");
        assertThrows(IllegalArgumentException.class, () -> seatHoldService.createBatch(noSlot));
        SeatHoldCreateRequest noOperator = createRequest(area1, List.of(deskId("DC001")), "甲");
        noOperator.setOperator(" ");
        assertThrows(IllegalArgumentException.class, () -> seatHoldService.createBatch(noOperator));
        assertThrows(IllegalArgumentException.class,
                () -> seatHoldService.createBatch(createRequest(area1, List.of(), "甲")));
        assertThrows(IllegalArgumentException.class,
                () -> seatHoldService.createBatch(createRequest(99999L, List.of(deskId("DC001")), "甲")));
    }

    @Test
    void createShouldRejectDisabledAssetAndCrossAreaAsset() {
        // DC003 已停用（无在占批次），不能勾选占住
        IllegalArgumentException ex1 = assertThrows(IllegalArgumentException.class,
                () -> seatHoldService.createBatch(createRequest(area1, List.of(deskId("DC003")), "甲")));
        assertTrue(ex1.getMessage().contains("DC003"));
        // DC004 属于分区2，不能占进分区1的批次
        IllegalArgumentException ex2 = assertThrows(IllegalArgumentException.class,
                () -> seatHoldService.createBatch(createRequest(area1, List.of(deskId("DC004")), "甲")));
        assertTrue(ex2.getMessage().contains("不属于占座分区"));
    }

    @Test
    void createShouldBlockAssetAlreadyHeldByAnotherOpenBatch() {
        SeatHoldBatch first = seatHoldService.createBatch(
                createRequest(area1, List.of(deskId("DC001")), "甲"));

        // 同一在占资产再开一批：明确提示已有批次
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> seatHoldService.createBatch(createRequest(area1, List.of(deskId("DC001")), "乙")));
        assertTrue(ex.getMessage().contains(first.getBatchNo()));
        assertTrue(ex.getMessage().contains("占住"));

        // 进行中在占资产可通过 active 接口查到
        List<SeatHoldItem> active = seatHoldService.findOpenHolds(area1);
        assertEquals(1, active.size());
        assertEquals(first.getBatchNo(), active.get(0).getBatchNo());
    }

    @Test
    void releaseShouldRestoreDeskAndUpdateCounts() {
        SeatHoldBatch batch = seatHoldService.createBatch(
                createRequest(area1, List.of(deskId("DC001"), deskId("DC002")), "甲"));
        Long itemId = itemOf(batch, "DC001").getId();

        SeatHoldBatch after = seatHoldService.release(batch.getId(), itemId, handle("甲"));

        assertEquals(1, after.getHeldCount());
        assertEquals(1, after.getReleasedCount());
        assertEquals(SeatHoldItem.STATUS_RELEASED, itemOf(after, "DC001").getItemStatus());
        assertEquals(1, deskStatus("DC001"), "当场释放后桌椅恢复可用");
        assertEquals(0, deskStatus("DC002"), "未释放资产保持停用");
        assertNotNull(itemOf(after, "DC001").getReleasedAt());
        assertEquals("甲", itemOf(after, "DC001").getReleasedBy());

        // 已释放资产可以在新一批中重新占住
        SeatHoldBatch next = seatHoldService.createBatch(
                createRequest(area1, List.of(deskId("DC001")), "乙"));
        assertEquals(SeatHoldBatch.STATUS_OPEN, next.getStatus());
        assertEquals(0, deskStatus("DC001"));
    }

    @Test
    void timeoutShouldKeepDeskDisabledAndRevertShouldBringItBack() {
        SeatHoldBatch batch = seatHoldService.createBatch(
                createRequest(area1, List.of(deskId("DC001")), "甲"));
        Long itemId = itemOf(batch, "DC001").getId();

        SeatHoldBatch timed = seatHoldService.markTimeout(batch.getId(), itemId, handle("甲"));
        assertEquals(SeatHoldItem.STATUS_TIMEOUT, itemOf(timed, "DC001").getItemStatus());
        assertEquals(1, timed.getTimeoutCount());
        assertEquals(0, timed.getHeldCount());
        assertEquals(0, deskStatus("DC001"), "超时未到期间桌椅继续停用");

        // 超时未到资产也不能被另一批占用，提示中标明超时未到
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> seatHoldService.createBatch(createRequest(area1, List.of(deskId("DC001")), "乙")));
        assertTrue(ex.getMessage().contains("超时未到"));

        // 撤回超时标记回到在占
        SeatHoldBatch reverted = seatHoldService.revertTimeout(batch.getId(), itemId, handle("甲"));
        assertEquals(SeatHoldItem.STATUS_HOLDING, itemOf(reverted, "DC001").getItemStatus());
        assertEquals(1, reverted.getHeldCount());
        assertEquals(0, reverted.getTimeoutCount());
        assertNull(itemOf(reverted, "DC001").getTimeoutBy());
        assertNull(itemOf(reverted, "DC001").getTimeoutAt());
    }

    @Test
    void illegalTransitionsShouldBeRejected() {
        SeatHoldBatch batch = seatHoldService.createBatch(
                createRequest(area1, List.of(deskId("DC001"), deskId("DC002")), "甲"));
        Long item1 = itemOf(batch, "DC001").getId();
        seatHoldService.release(batch.getId(), item1, handle("甲"));

        // 已释放不能再释放/标超时
        assertThrows(IllegalArgumentException.class,
                () -> seatHoldService.release(batch.getId(), item1, handle("甲")));
        assertThrows(IllegalArgumentException.class,
                () -> seatHoldService.markTimeout(batch.getId(), item1, handle("甲")));

        Long item2 = itemOf(batch, "DC002").getId();
        seatHoldService.markTimeout(batch.getId(), item2, handle("甲"));
        // 超时状态不能当场释放（整批结束后走遗留处置）
        assertThrows(IllegalArgumentException.class,
                () -> seatHoldService.release(batch.getId(), item2, handle("甲")));
        // 在占才能标超时
        assertThrows(IllegalArgumentException.class,
                () -> seatHoldService.markTimeout(batch.getId(), item2, handle("甲")));
    }

    @Test
    void holdShouldAppendAssetsButRejectDuplicates() {
        SeatHoldBatch batch = seatHoldService.createBatch(
                createRequest(area1, List.of(deskId("DC001")), "甲"));

        SeatHoldBatch appended = seatHoldService.hold(batch.getId(),
                holdRequest(List.of(deskId("DC002")), "甲"));
        assertEquals(2, appended.getTotalCount());
        assertEquals(2, appended.getHeldCount());
        assertEquals(0, deskStatus("DC002"));

        // 同批重复勾选在占资产被阻止
        assertThrows(IllegalArgumentException.class, () -> seatHoldService.hold(batch.getId(),
                holdRequest(List.of(deskId("DC001")), "甲")));
    }

    @Test
    void holdShouldRejectAssetAlreadyReleasedInSameBatchButAllowInNewBatch() {
        SeatHoldBatch batch = seatHoldService.createBatch(
                createRequest(area1, List.of(deskId("DC001")), "甲"));
        Long itemId = itemOf(batch, "DC001").getId();
        seatHoldService.release(batch.getId(), itemId, handle("甲"));
        assertEquals(1, deskStatus("DC001"));

        // 同批已释放资产不重新占入，避免一件资产一批两条明细
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> seatHoldService.hold(batch.getId(), holdRequest(List.of(deskId("DC001")), "甲")));
        assertTrue(ex.getMessage().contains("同一批次不能重复占住"));

        // 另开新批可以重新占住
        SeatHoldBatch next = seatHoldService.createBatch(
                createRequest(area1, List.of(deskId("DC001")), "乙"));
        assertEquals(SeatHoldBatch.STATUS_OPEN, next.getStatus());
        assertEquals(0, deskStatus("DC001"));
    }

    @Test
    void finishShouldKeepHeldAndTimeoutDisabledWhileReleasedStayAvailable() {
        SeatHoldBatch batch = seatHoldService.createBatch(
                createRequest(area1, List.of(deskId("DC001"), deskId("DC002")), "甲"));
        Long item1 = itemOf(batch, "DC001").getId();
        seatHoldService.release(batch.getId(), item1, handle("甲"));
        // DC002 保持在占至整批结束（模拟无人到场）
        SeatHoldBatch ended = seatHoldService.finishBatch(batch.getId(), handle("甲"));

        assertEquals(SeatHoldBatch.STATUS_ENDED, ended.getStatus());
        assertNotNull(ended.getEndedAt());
        assertEquals(1, deskStatus("DC001"), "已释放的恢复可用并保持");
        assertEquals(0, deskStatus("DC002"), "仍占着的资产整批结束后保持停用");

        // 已结束批次禁止进行中操作
        assertThrows(IllegalArgumentException.class,
                () -> seatHoldService.hold(batch.getId(), holdRequest(List.of(deskId("DC001")), "甲")));
        assertThrows(IllegalArgumentException.class,
                () -> seatHoldService.release(batch.getId(), item1, handle("甲")));
        assertThrows(IllegalArgumentException.class,
                () -> seatHoldService.finishBatch(batch.getId(), handle("甲")));

        // 遗留停用资产清场确认后逐条释放恢复
        Long item2 = itemOf(ended, "DC002").getId();
        SeatHoldBatch cleared = seatHoldService.releaseLegacy(batch.getId(), item2, handle("清场员"));
        assertEquals(2, cleared.getReleasedCount());
        assertEquals(0, cleared.getHeldCount());
        assertEquals(1, deskStatus("DC002"));
        // 已结束批次已释放明细不能重复处置
        assertThrows(IllegalArgumentException.class,
                () -> seatHoldService.releaseLegacy(batch.getId(), item2, handle("清场员")));
        // 进行中批次不能走遗留释放入口
        SeatHoldBatch open = seatHoldService.createBatch(
                createRequest(area2, List.of(deskId("DC004")), "乙"));
        assertThrows(IllegalArgumentException.class,
                () -> seatHoldService.releaseLegacy(open.getId(),
                        itemOf(open, "DC004").getId(), handle("清场员")));
    }

    @Test
    void searchShouldFilterByAreaAndStatus() {
        SeatHoldBatch batch = seatHoldService.createBatch(
                createRequest(area1, List.of(deskId("DC001")), "甲"));
        seatHoldService.finishBatch(batch.getId(), handle("甲"));
        seatHoldService.createBatch(createRequest(area2, List.of(deskId("DC004")), "乙"));

        assertEquals(2, seatHoldService.search(null, null).size());
        assertEquals(1, seatHoldService.search(area1, null).size());
        assertEquals(1, seatHoldService.search(null, SeatHoldBatch.STATUS_OPEN).size());
        assertEquals(1, seatHoldService.search(area1, SeatHoldBatch.STATUS_ENDED).size());
        assertEquals(0, seatHoldService.search(area2, SeatHoldBatch.STATUS_ENDED).size());
        assertThrows(IllegalArgumentException.class, () -> seatHoldService.search(null, "BOGUS"));
    }

    @Test
    void clearingShouldSeparateStillDisabledDesksIntoThreeGroups() {
        createDeskChair("DC005", area1, 1);
        createDeskChair("DC006", area1, 1);
        SeatHoldBatch batch = seatHoldService.createBatch(createRequest(area1,
                List.of(deskId("DC001"), deskId("DC002"), deskId("DC005"), deskId("DC006")), "甲"));

        // DC002 超时未到；DC005 占住期间被报修；DC006 当场释放恢复可用后又被报修停用
        seatHoldService.markTimeout(batch.getId(), itemOf(batch, "DC002").getId(), handle("甲"));
        createOpenRepair("DC005", area1);
        seatHoldService.release(batch.getId(), itemOf(batch, "DC006").getId(), handle("甲"));
        assertEquals(1, deskStatus("DC006"));
        createOpenRepair("DC006", area1);
        assertEquals(0, deskStatus("DC006"));

        SeatHoldBatch ended = seatHoldService.finishBatch(batch.getId(), handle("甲"));
        SeatHoldClearingVO clearing = seatHoldService.getClearing(ended.getId());

        assertEquals(ended.getId(), clearing.getBatchId());
        assertEquals(SeatHoldBatch.STATUS_ENDED, clearing.getBatchStatus());
        // 四件仍停用：1 应恢复 + 1 超时未到 + 2 报修停用
        assertEquals(4, clearing.getStillDisabledCount());

        assertEquals(1, clearing.getRestorableItems().size());
        SeatHoldClearingItemVO restorable = clearing.getRestorableItems().get(0);
        assertEquals("DC001", restorable.getAssetCode());
        assertEquals(SeatHoldItem.STATUS_HOLDING, restorable.getItemStatus());
        assertEquals(1, restorable.getPreviousDeskStatus());
        assertEquals(0, restorable.getDeskStatus());
        assertEquals(0, restorable.getOpenRepairCount());
        assertTrue(restorable.getReleasable());

        assertEquals(1, clearing.getTimeoutDisabledItems().size());
        SeatHoldClearingItemVO timeout = clearing.getTimeoutDisabledItems().get(0);
        assertEquals("DC002", timeout.getAssetCode());
        assertEquals(SeatHoldItem.STATUS_TIMEOUT, timeout.getItemStatus());
        assertNotNull(timeout.getTimeoutBy());
        assertTrue(timeout.getReleasable());

        // 报修停用组：占住中报修的 DC005 + 已释放后报修的 DC006，报修锁定优先于明细状态
        assertEquals(2, clearing.getRepairDisabledItems().size());
        SeatHoldClearingItemVO repairHolding = clearing.getRepairDisabledItems().stream()
                .filter(r -> "DC005".equals(r.getAssetCode())).findFirst().orElseThrow();
        assertEquals(SeatHoldItem.STATUS_HOLDING, repairHolding.getItemStatus());
        assertEquals(1, repairHolding.getOpenRepairCount());
        assertNotNull(repairHolding.getOpenRepairOrderNos());
        assertTrue(repairHolding.getReleasable(), "报修中的在占明细仍可释放闭环，但桌椅保持停用");
        SeatHoldClearingItemVO repairReleased = clearing.getRepairDisabledItems().stream()
                .filter(r -> "DC006".equals(r.getAssetCode())).findFirst().orElseThrow();
        assertEquals(SeatHoldItem.STATUS_RELEASED, repairReleased.getItemStatus());
        assertFalse(repairReleased.getReleasable(), "已释放明细无需重复处置");
    }

    @Test
    void legacyReleaseShouldWriteBackPreviousStatusButKeepRepairDisabled() {
        createDeskChair("DC005", area1, 1);
        SeatHoldBatch batch = seatHoldService.createBatch(
                createRequest(area1, List.of(deskId("DC001"), deskId("DC005")), "甲"));
        createOpenRepair("DC005", area1);
        seatHoldService.finishBatch(batch.getId(), handle("甲"));

        // 应按占住前状态恢复：DC001 清场释放后回写占住前状态（可用），退出清场清单
        seatHoldService.releaseLegacy(batch.getId(), itemOf(batch, "DC001").getId(), handle("清场员"));
        assertEquals(1, deskStatus("DC001"));

        // 档案报修仍停用：DC005 释放只闭环占座明细，报修未闭环不能恢复成可用
        seatHoldService.releaseLegacy(batch.getId(), itemOf(batch, "DC005").getId(), handle("清场员"));
        assertEquals(0, deskStatus("DC005"), "报修未闭环的桌椅不能被清场释放恢复成可用");

        // 刷新后清场清单、桌椅档案状态和占座明细对得上
        SeatHoldClearingVO clearing = seatHoldService.getClearing(batch.getId());
        assertEquals(1, clearing.getStillDisabledCount());
        assertEquals(0, clearing.getRestorableItems().size());
        assertEquals(0, clearing.getTimeoutDisabledItems().size());
        assertEquals(1, clearing.getRepairDisabledItems().size());
        SeatHoldClearingItemVO row = clearing.getRepairDisabledItems().get(0);
        assertEquals("DC005", row.getAssetCode());
        assertEquals(SeatHoldItem.STATUS_RELEASED, row.getItemStatus());
        assertEquals(0, row.getDeskStatus());
        assertFalse(row.getReleasable());

        SeatHoldBatch detail = seatHoldService.findById(batch.getId());
        assertEquals(2, detail.getReleasedCount());
        assertEquals(0, detail.getHeldCount());
        assertEquals(SeatHoldItem.STATUS_RELEASED, itemOf(detail, "DC005").getItemStatus());
        assertEquals(0, itemOf(detail, "DC005").getDeskStatus());
        assertEquals(1, itemOf(detail, "DC001").getDeskStatus());
    }

    @Test
    void clearingShouldRejectOpenBatch() {
        SeatHoldBatch batch = seatHoldService.createBatch(
                createRequest(area1, List.of(deskId("DC001")), "甲"));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> seatHoldService.getClearing(batch.getId()));
        assertTrue(ex.getMessage().contains("整批结束"));
    }

    @Test
    void createShouldRejectAreaMarkedClosedTodayWithEndTimeInMessage() {
        LocalDateTime closedUntil = LocalDateTime.now().plusHours(3);
        markAreaClosed(area1, closedUntil);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> seatHoldService.createBatch(createRequest(area1, List.of(deskId("DC001")), "甲")));
        assertTrue(ex.getMessage().contains("闭馆"));
        // 拦截消息必须带出闭馆结束时刻，值班员才知道何时解禁
        assertTrue(ex.getMessage().contains(closedUntil.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))));
    }

    @Test
    void expiredClosureShouldAllowOpeningNewBatchInSameArea() {
        // 闭馆结束时刻已过：挂牌记录仍在库里（null 不被改写），但不再拦截开批
        markAreaClosed(area1, LocalDateTime.now().minusMinutes(1));

        SeatHoldBatch batch = seatHoldService.createBatch(
                createRequest(area1, List.of(deskId("DC001")), "甲"));
        assertEquals(SeatHoldBatch.STATUS_OPEN, batch.getStatus());
        assertEquals(0, deskStatus("DC001"));
        assertNotNull(readingAreaMapper.selectById(area1).getClosedUntil(),
                "到期后保留闭馆挂牌历史值，由看板/分区页按当前时刻判定是否闭馆中");
    }

    @Test
    void clearClosedEarlyShouldReopenAreaImmediately() {
        markAreaClosed(area1, LocalDateTime.now().plusHours(2));
        assertThrows(IllegalArgumentException.class,
                () -> seatHoldService.createBatch(createRequest(area1, List.of(deskId("DC001")), "甲")));

        readingAreaService.clearClosed(area1);
        assertNull(readingAreaMapper.selectById(area1).getClosedUntil());
        SeatHoldBatch batch = seatHoldService.createBatch(
                createRequest(area1, List.of(deskId("DC001")), "甲"));
        assertEquals(SeatHoldBatch.STATUS_OPEN, batch.getStatus());
    }

    private void markAreaClosed(Long areaId, LocalDateTime closedUntil) {
        jdbcTemplate.update("UPDATE reading_area SET closed_until = ? WHERE id = ?", closedUntil, areaId);
    }
}
