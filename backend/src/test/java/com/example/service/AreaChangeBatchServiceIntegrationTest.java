package com.example.service;

import com.example.TestRedisConfig;
import com.example.dto.BatchTransferRequest;
import com.example.entity.AreaChangeBatch;
import com.example.entity.AreaChangeLog;
import com.example.entity.DeskChair;
import com.example.entity.ReadingArea;
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
}
