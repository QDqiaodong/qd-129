package com.example.service;

import com.example.TestRedisConfig;
import com.example.dto.BatchTransferRequest;
import com.example.dto.SeatHoldCreateRequest;
import com.example.entity.AreaChangeBatch;
import com.example.entity.AreaChangeBatchItem;
import com.example.entity.AreaChangeLog;
import com.example.entity.DeskChair;
import com.example.entity.ReadingArea;
import com.example.entity.SeatHoldBatch;
import com.example.mapper.AreaChangeLogMapper;
import com.example.mapper.DeskChairMapper;
import com.example.mapper.ReadingAreaMapper;
import com.example.vo.BatchTransferPreviewVO;
import com.example.vo.BatchTransferResultVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Import;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;

@SpringBootTest(properties = "spring.sql.init.mode=always")
@Import(TestRedisConfig.class)
class AreaChangeBatchServiceIntegrationTest {

    @Autowired
    private AreaChangeBatchService areaChangeBatchService;

    @Autowired
    private DeskChairService deskChairService;

    @Autowired
    private DeskChairMapper deskChairMapper;

    @Autowired
    private ReadingAreaMapper readingAreaMapper;

    @Autowired
    private SeatHoldService seatHoldService;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @SpyBean
    private AreaChangeLogMapper areaChangeLogMapper;

    private Long area1;
    private Long area2;
    private Long area3;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("DELETE FROM area_change_batch_item");
        jdbcTemplate.execute("DELETE FROM area_change_batch");
        jdbcTemplate.execute("DELETE FROM area_change_log_00");
        jdbcTemplate.execute("DELETE FROM area_change_log_01");
        jdbcTemplate.execute("DELETE FROM seat_hold_item");
        jdbcTemplate.execute("DELETE FROM seat_hold_batch");
        jdbcTemplate.execute("DELETE FROM repair_order");
        jdbcTemplate.execute("DELETE FROM desk_chair_tag");
        jdbcTemplate.execute("DELETE FROM desk_chair");
        jdbcTemplate.execute("DELETE FROM reading_area");

        area1 = createArea("A001", "第一阅览区");
        area2 = createArea("A002", "第二阅览区");
        area3 = createArea("B001", "第三阅览区");

        // 4 张桌椅在分区1，2 张在分区2；ID 奇偶都会落到两个日志分片表
        createDeskChair("DC001", area1);
        createDeskChair("DC002", area1);
        createDeskChair("DC003", area1);
        createDeskChair("DC004", area1);
        createDeskChair("DC005", area2);
        createDeskChair("DC006", area2);
    }

    private Long createArea(String code, String name) {
        ReadingArea area = new ReadingArea();
        area.setAreaCode(code);
        area.setAreaName(name);
        area.setStatus(1);
        readingAreaMapper.insert(area);
        return area.getId();
    }

    private void createDeskChair(String assetCode, Long areaId) {
        DeskChair dc = new DeskChair();
        dc.setAssetCode(assetCode);
        dc.setCapacity(2);
        dc.setAreaId(areaId);
        dc.setDimensions("120x60x75cm");
        dc.setStatus(1);
        deskChairMapper.insert(dc);
    }

    private BatchTransferRequest request(List<Long> ids, Long targetAreaId, String reason, String operator) {
        BatchTransferRequest req = new BatchTransferRequest();
        req.setDeskChairIds(ids);
        req.setTargetAreaId(targetAreaId);
        req.setChangeReason(reason);
        req.setOperator(operator);
        return req;
    }

    private List<Long> idsInArea1() {
        return deskChairService.findByAreaId(area1).stream().map(DeskChair::getId).toList();
    }

    @Test
    void previewShouldReportMoveCountAndOwnership() {
        BatchTransferPreviewVO preview = areaChangeBatchService.preview(
                request(idsInArea1(), area3, null, null));

        assertEquals(4, preview.getSelectedCount());
        assertEquals(4, preview.getMoveCount());
        assertEquals(0, preview.getInvalidCount());
        assertTrue(preview.getCanSubmit());
        assertEquals("第三阅览区", preview.getTargetAreaName());
        assertTrue(preview.getItems().stream().allMatch(item ->
                "第一阅览区".equals(item.getOldAreaName())
                        && "第三阅览区".equals(item.getNewAreaName())));
    }

    @Test
    void previewShouldBlockWhenAssetAlreadyInTargetArea() {
        BatchTransferPreviewVO preview = areaChangeBatchService.preview(
                request(idsInArea1(), area1, null, null));

        assertEquals(4, preview.getSelectedCount());
        assertEquals(0, preview.getMoveCount());
        assertEquals(4, preview.getAlreadyInTargetCount());
        assertFalse(preview.getCanSubmit());
    }

    @Test
    void executeShouldMoveAllWriteShardedLogsAndPersistSuccessBatch() {
        List<Long> ids = idsInArea1();
        BatchTransferResultVO result = areaChangeBatchService.execute(
                request(ids, area3, "楼层装修调整", "张三"));

        assertEquals("SUCCESS", result.getStatus());
        assertEquals(4, result.getTotalCount());
        assertEquals(4, result.getSuccessCount());
        assertEquals(0, result.getFailCount());
        assertNotNull(result.getBatchNo());
        assertTrue(result.getBatchNo().startsWith("BAT"));
        assertTrue(result.getItems().stream().allMatch(item -> "SUCCESS".equals(item.getStatus())));

        // 每张桌椅都已迁移
        ids.forEach(id -> assertEquals(area3, deskChairMapper.selectById(id).getAreaId()));

        // 每张桌椅都写了一条变更记录，且带批次号，分片按奇偶落到 00/01 两张表
        List<AreaChangeLog> logs00 = areaChangeLogMapper.findByBatchNoFromTable00(result.getBatchNo());
        List<AreaChangeLog> logs01 = areaChangeLogMapper.findByBatchNoFromTable01(result.getBatchNo());
        assertEquals(4, logs00.size() + logs01.size());
        List<AreaChangeLog> allLogs = new java.util.ArrayList<>(logs00);
        allLogs.addAll(logs01);
        assertTrue(allLogs.stream().allMatch(log ->
                "楼层装修调整".equals(log.getChangeReason())
                        && "张三".equals(log.getOperator())
                        && area3.equals(log.getNewAreaId())
                        && area1.equals(log.getOldAreaId())));

        // 批次结果可追溯
        AreaChangeBatch stored = areaChangeBatchService.findById(result.getBatchId());
        assertEquals("SUCCESS", stored.getStatus());
        assertEquals(4, stored.getItems().size());
        assertTrue(stored.getItems().stream().allMatch(item -> item.getChangeLogId() != null));
    }

    @Test
    void executeShouldRejectMissingReasonOrOperator() {
        List<Long> ids = idsInArea1();
        assertThrows(IllegalArgumentException.class, () ->
                areaChangeBatchService.execute(request(ids, area3, "  ", "张三")));
        assertThrows(IllegalArgumentException.class, () ->
                areaChangeBatchService.execute(request(ids, area3, "原因", null)));
        // 未发生迁移
        ids.forEach(id -> assertEquals(area1, deskChairMapper.selectById(id).getAreaId()));
    }

    @Test
    void executeShouldRecordFailedBatchWhenValidationFailsAndMoveNothing() {
        List<Long> allIds = deskChairService.findAll().stream().map(DeskChair::getId).toList();
        BatchTransferResultVO result = areaChangeBatchService.execute(
                request(allIds, area2, "尝试迁移", "李四"));

        assertEquals("FAILED", result.getStatus());
        assertEquals(6, result.getTotalCount());
        assertEquals(0, result.getSuccessCount());
        assertEquals(6, result.getFailCount());
        assertNotNull(result.getErrorMessage());
        // 分区2 的两张桌椅本来就在目标分区
        assertEquals(2, result.getItems().stream()
                .filter(item -> "已在目标分区，无需迁移".equals(item.getErrorMessage())).count());

        // 分区1 的桌椅未被迁移，分区2 的桌椅保持不变
        assertEquals(4, deskChairService.findByAreaId(area1).size());
        assertEquals(2, deskChairService.findByAreaId(area2).size());
        assertEquals(0, areaChangeLogMapper.findAllFromTable00().size());
        assertEquals(0, areaChangeLogMapper.findAllFromTable01().size());

        // 失败批次同样可追溯
        List<AreaChangeBatch> batches = areaChangeBatchService.findAll();
        assertEquals(1, batches.size());
        assertEquals("FAILED", batches.get(0).getStatus());
        assertEquals(6, areaChangeBatchService.findById(batches.get(0).getId()).getItems().size());
    }

    @Test
    void executeShouldRollbackEverythingWhenShardedLogInsertFails() {
        List<Long> ids = idsInArea1();
        Long failingId = ids.get(1); // 处理到第二张时注入故障

        Mockito.doAnswer(invocation -> {
            AreaChangeLog log = invocation.getArgument(0);
            if (failingId.equals(log.getDeskChairId())) {
                throw new RuntimeException("模拟日志分片写入失败");
            }
            return invocation.callRealMethod();
        }).when(areaChangeLogMapper).insertLog(any(), anyString());

        BatchTransferResultVO result = areaChangeBatchService.execute(
                request(ids, area3, "整批回滚测试", "王五"));

        assertEquals("FAILED", result.getStatus());
        assertEquals(4, result.getTotalCount());
        assertEquals(0, result.getSuccessCount());
        assertEquals(4, result.getFailCount());
        assertNotNull(result.getErrorMessage());
        assertTrue(result.getErrorMessage().contains("回滚"));

        // 关键：所有桌椅归属都回滚到原分区（含故障前已更新的第一张）
        ids.forEach(id -> assertEquals(area1, deskChairMapper.selectById(id).getAreaId()));

        // 主事务回滚，未残留任何变更日志；失败批次由独立事务记录，仍可追溯
        assertEquals(0, areaChangeLogMapper.findByBatchNoFromTable00(result.getBatchNo()).size()
                + areaChangeLogMapper.findByBatchNoFromTable01(result.getBatchNo()).size());
        AreaChangeBatch failedBatch = areaChangeBatchService.findById(result.getBatchId());
        assertEquals("FAILED", failedBatch.getStatus());
        assertEquals(4, failedBatch.getItems().size());

        Mockito.reset(areaChangeLogMapper);
    }

    @Test
    void searchShouldFilterByAreaAndTagsCombined() {
        List<DeskChair> byArea = deskChairService.search(area2, null);
        assertEquals(2, byArea.size());

        List<DeskChair> all = deskChairService.search(null, null);
        assertEquals(6, all.size());

        List<DeskChair> byBoth = deskChairService.search(area1, Arrays.asList(999L));
        assertEquals(0, byBoth.size());
    }

    @Test
    void previewShouldDeduplicateRepeatedSelectionsAcrossFilters() {
        // 模拟前端跨筛选重复勾选同一批资产
        List<Long> ids = idsInArea1();
        List<Long> repeatedIds = new java.util.ArrayList<>(ids);
        repeatedIds.addAll(ids);

        BatchTransferPreviewVO preview = areaChangeBatchService.preview(
                request(repeatedIds, area3, null, null));

        assertEquals(4, preview.getSelectedCount());
        assertEquals(4, preview.getMoveCount());
        assertEquals(0, preview.getInvalidCount());
        assertEquals(4, preview.getItems().size());
        assertTrue(preview.getCanSubmit());
    }

    @Test
    void previewShouldReportDeletedDisabledAndAlreadyInTargetAssetsItemByItem() {
        List<Long> ids = new java.util.ArrayList<>(idsInArea1());
        Long deletedId = ids.get(0);
        Long disabledId = ids.get(1);
        Long readyId = ids.get(2);
        Long alreadyId = ids.get(3);

        deskChairMapper.deleteById(deletedId);
        DeskChair disabled = deskChairMapper.selectById(disabledId);
        disabled.setStatus(0);
        deskChairMapper.updateById(disabled);
        // alreadyId 先迁到 area3，再勾选 area3 作为目标，形成“已在目标分区”；readyId 保持可迁移
        DeskChair already = deskChairMapper.selectById(alreadyId);
        already.setAreaId(area3);
        deskChairMapper.updateById(already);

        BatchTransferPreviewVO preview = areaChangeBatchService.preview(
                request(ids, area3, null, null));

        assertEquals(4, preview.getSelectedCount());
        assertEquals(1, preview.getMoveCount());
        assertEquals(3, preview.getInvalidCount());
        assertEquals(1, preview.getAlreadyInTargetCount());
        assertFalse(preview.getCanSubmit());

        java.util.Map<Long, com.example.vo.BatchTransferItemVO> itemMap = preview.getItems().stream()
                .collect(java.util.stream.Collectors.toMap(
                        com.example.vo.BatchTransferItemVO::getDeskChairId, item -> item));

        com.example.vo.BatchTransferItemVO deletedItem = itemMap.get(deletedId);
        assertFalse(deletedItem.getValid());
        assertEquals("NOT_FOUND", deletedItem.getReasonCode());
        assertTrue(deletedItem.getErrorMessage().contains("不存在") || deletedItem.getErrorMessage().contains("删除"));

        com.example.vo.BatchTransferItemVO disabledItem = itemMap.get(disabledId);
        assertFalse(disabledItem.getValid());
        assertEquals("DISABLED", disabledItem.getReasonCode());
        assertEquals("资产已停用，无法调区", disabledItem.getErrorMessage());
        assertEquals("DC002", disabledItem.getAssetCode());

        com.example.vo.BatchTransferItemVO readyItem = itemMap.get(readyId);
        assertTrue(readyItem.getValid());
        assertEquals("READY", readyItem.getStatus());

        com.example.vo.BatchTransferItemVO alreadyItem = itemMap.get(alreadyId);
        assertFalse(alreadyItem.getValid());
        assertEquals("ALREADY_IN_TARGET", alreadyItem.getReasonCode());
        assertEquals("已在目标分区，无需迁移", alreadyItem.getErrorMessage());
    }

    @Test
    void executeShouldDeduplicateRepeatedIdsAndMoveEachAssetOnce() {
        List<Long> ids = idsInArea1();
        List<Long> repeatedIds = new java.util.ArrayList<>(ids);
        repeatedIds.addAll(ids);
        repeatedIds.add(ids.get(0));

        BatchTransferResultVO result = areaChangeBatchService.execute(
                request(repeatedIds, area3, "重复勾选提交", "赵六"));

        assertEquals("SUCCESS", result.getStatus());
        // 批次数量按去重后的唯一资产统计，与界面“已勾选”数量一致
        assertEquals(4, result.getTotalCount());
        assertEquals(4, result.getSuccessCount());
        assertEquals(0, result.getFailCount());
        assertEquals(4, result.getItems().size());
        long distinctItems = result.getItems().stream()
                .map(com.example.vo.BatchTransferItemVO::getDeskChairId).distinct().count();
        assertEquals(4, distinctItems);

        ids.forEach(id -> assertEquals(area3, deskChairMapper.selectById(id).getAreaId()));
        int logCount = areaChangeLogMapper.findByBatchNoFromTable00(result.getBatchNo()).size()
                + areaChangeLogMapper.findByBatchNoFromTable01(result.getBatchNo()).size();
        assertEquals(4, logCount);
    }

    @Test
    void executeShouldRejectInvalidAssetsItemByItemAndRollbackAllMoves() {
        List<Long> validIds = new java.util.ArrayList<>(idsInArea1());
        Long deletedId = validIds.remove(0);
        Long disabledId = validIds.remove(0);

        deskChairMapper.deleteById(deletedId);
        DeskChair disabled = deskChairMapper.selectById(disabledId);
        disabled.setStatus(0);
        deskChairMapper.updateById(disabled);

        List<Long> submitIds = new java.util.ArrayList<>(validIds);
        submitIds.add(deletedId);
        submitIds.add(disabledId);

        BatchTransferResultVO result = areaChangeBatchService.execute(
                request(submitIds, area3, "含失效资产提交", "孙七"));

        assertEquals("FAILED", result.getStatus());
        // 整批数量覆盖全部唯一勾选，逐项给出原因
        assertEquals(4, result.getTotalCount());
        assertEquals(0, result.getSuccessCount());
        assertEquals(4, result.getFailCount());

        java.util.Map<Long, String> messages = result.getItems().stream()
                .collect(java.util.stream.Collectors.toMap(
                        com.example.vo.BatchTransferItemVO::getDeskChairId,
                        com.example.vo.BatchTransferItemVO::getErrorMessage));
        assertTrue(messages.get(deletedId).contains("不存在") || messages.get(deletedId).contains("删除"));
        assertEquals("资产已停用，无法调区", messages.get(disabledId));
        // 本可迁移的两张资产随整批未执行，逐项标明是“随批跳过”，而不是资产本身失效
        validIds.forEach(id ->
                assertEquals("整批含不可迁移资产，未执行迁移", messages.get(id)));
        // 存在有效资产也不允许部分迁移：所有资产均保持原归属，整批回滚
        validIds.forEach(id -> assertEquals(area1, deskChairMapper.selectById(id).getAreaId()));
        assertEquals(0, areaChangeLogMapper.findAllFromTable00().size()
                + areaChangeLogMapper.findAllFromTable01().size());

        // 明细中失效资产仍可追溯（已删除资产无资产编号，但保留 ID）
        AreaChangeBatch stored = areaChangeBatchService.findById(result.getBatchId());
        assertEquals(4, stored.getItems().size());
    }

    private SeatHoldBatch holdInOpenBatch(Long areaId, List<Long> deskIds, String operator) {
        SeatHoldCreateRequest req = new SeatHoldCreateRequest();
        req.setAreaId(areaId);
        req.setTimeSlot("08:00-11:30 早高峰");
        req.setDeskChairIds(deskIds);
        req.setOperator(operator);
        return seatHoldService.createBatch(req);
    }

    private com.example.dto.SeatHoldHandleRequest holdHandle(String operator) {
        com.example.dto.SeatHoldHandleRequest req = new com.example.dto.SeatHoldHandleRequest();
        req.setOperator(operator);
        return req;
    }

    @Test
    void previewShouldReportSeatHoldingBatchNoInsteadOfGenericDisabled() {
        List<Long> ids = idsInArea1();
        Long heldId = ids.get(0);
        Long disabledId = ids.get(1);
        Long readyId = ids.get(2);
        String heldCode = deskChairMapper.selectById(heldId).getAssetCode();

        // heldId 被进行中占座批次占住（占住后档案状态为停用）；disabledId 仅档案停用（报修口径）
        SeatHoldBatch holdBatch = holdInOpenBatch(area1, List.of(heldId), "值班员甲");
        DeskChair disabled = deskChairMapper.selectById(disabledId);
        disabled.setStatus(0);
        deskChairMapper.updateById(disabled);

        BatchTransferPreviewVO preview = areaChangeBatchService.preview(
                request(ids, area3, null, null));

        assertEquals(4, preview.getSelectedCount());
        assertEquals(2, preview.getMoveCount());
        assertEquals(2, preview.getInvalidCount());
        assertEquals(1, preview.getSeatHoldingCount());
        assertFalse(preview.getCanSubmit());

        java.util.Map<Long, com.example.vo.BatchTransferItemVO> itemMap = preview.getItems().stream()
                .collect(java.util.stream.Collectors.toMap(
                        com.example.vo.BatchTransferItemVO::getDeskChairId, item -> item));

        // 占座资产：原因码为 SEAT_HOLDING，提示写明进行中占座批次号，与占座列表对得上
        com.example.vo.BatchTransferItemVO heldItem = itemMap.get(heldId);
        assertFalse(heldItem.getValid());
        assertEquals("SEAT_HOLDING", heldItem.getReasonCode());
        assertEquals(holdBatch.getBatchNo(), heldItem.getSeatHoldBatchNo());
        assertTrue(heldItem.getErrorMessage().contains(holdBatch.getBatchNo()));
        assertTrue(heldItem.getErrorMessage().contains("进行中占座批次"));
        assertEquals(heldCode, heldItem.getAssetCode());

        // 档案停用资产：仍走原来的停用说明，不挂占座批次
        com.example.vo.BatchTransferItemVO disabledItem = itemMap.get(disabledId);
        assertEquals("DISABLED", disabledItem.getReasonCode());
        assertNull(disabledItem.getSeatHoldBatchNo());
        assertEquals("资产已停用，无法调区", disabledItem.getErrorMessage());

        assertTrue(itemMap.get(readyId).getValid());
    }

    @Test
    void previewShouldTreatEndedBatchLegacyDisabledAsPlainDisabledAndMatchHoldList() {
        List<Long> ids = idsInArea1();
        Long heldId = ids.get(0);
        SeatHoldBatch holdBatch = holdInOpenBatch(area1, List.of(heldId), "值班员甲");

        // 整批结束后遗留资产保持停用：占座列表不再展示，调区原因回到档案停用
        seatHoldService.finishBatch(holdBatch.getId(), holdHandle("值班员甲"));

        BatchTransferPreviewVO preview = areaChangeBatchService.preview(
                request(ids, area3, null, null));
        com.example.vo.BatchTransferItemVO heldItem = preview.getItems().stream()
                .filter(item -> heldId.equals(item.getDeskChairId())).findFirst().orElseThrow();
        assertEquals("DISABLED", heldItem.getReasonCode());
        assertNull(heldItem.getSeatHoldBatchNo());
        assertEquals("资产已停用，无法调区", heldItem.getErrorMessage());

        // 进行中占座列表已无该资产，口径一致
        assertEquals(0, seatHoldService.findOpenHolds(area1).size());
    }

    @Test
    void executeShouldRecordHoldingBatchNoInFailedHeaderAndItemMessages() {
        List<Long> ids = new java.util.ArrayList<>(idsInArea1());
        Long heldId = ids.get(0);
        SeatHoldBatch holdBatch = holdInOpenBatch(area1, List.of(heldId), "值班员甲");

        BatchTransferResultVO result = areaChangeBatchService.execute(
                request(ids, area3, "高峰后调区", "李四"));

        assertEquals("FAILED", result.getStatus());
        assertEquals(4, result.getTotalCount());
        assertEquals(0, result.getSuccessCount());
        assertEquals(4, result.getFailCount());

        // 批次头失败原因汇总出拦截本批的进行中占座批次号
        assertNotNull(result.getErrorMessage());
        assertTrue(result.getErrorMessage().contains(holdBatch.getBatchNo()));
        assertTrue(result.getErrorMessage().contains("进行中占座批次"));

        java.util.Map<Long, String> messages = result.getItems().stream()
                .collect(java.util.stream.Collectors.toMap(
                        com.example.vo.BatchTransferItemVO::getDeskChairId,
                        com.example.vo.BatchTransferItemVO::getErrorMessage));
        // 占座资产逐项原因写明批次号；其余资产随整批跳过
        assertTrue(messages.get(heldId).contains(holdBatch.getBatchNo()));
        assertTrue(messages.get(heldId).contains("进行中占座批次"));
        ids.stream().filter(id -> !id.equals(heldId))
                .forEach(id -> assertEquals("整批含不可迁移资产，未执行迁移", messages.get(id)));

        // 任何资产都未迁移
        ids.forEach(id -> assertEquals(area1, deskChairMapper.selectById(id).getAreaId()));

        // 持久化的失败批次明细仍带占座批次号，刷新后可与占座列表核对
        AreaChangeBatch stored = areaChangeBatchService.findById(result.getBatchId());
        AreaChangeBatchItem storedHeldItem = stored.getItems().stream()
                .filter(item -> heldId.equals(item.getDeskChairId())).findFirst().orElseThrow();
        assertTrue(storedHeldItem.getErrorMessage().contains(holdBatch.getBatchNo()));
    }
}
