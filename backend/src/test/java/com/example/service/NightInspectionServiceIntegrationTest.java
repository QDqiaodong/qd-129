package com.example.service;

import com.example.TestRedisConfig;
import com.example.dto.NightInspectionCheckRequest;
import com.example.dto.NightInspectionCreateRequest;
import com.example.dto.NightInspectionHandleRequest;
import com.example.entity.DeskChair;
import com.example.entity.NightInspectionBatch;
import com.example.entity.NightInspectionItem;
import com.example.entity.ReadingArea;
import com.example.mapper.DeskChairMapper;
import com.example.mapper.ReadingAreaMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = "spring.sql.init.mode=always")
@Import(TestRedisConfig.class)
class NightInspectionServiceIntegrationTest {

    @Autowired
    private NightInspectionService nightInspectionService;

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
        jdbcTemplate.execute("DELETE FROM night_inspection_item");
        jdbcTemplate.execute("DELETE FROM night_inspection_batch");
        jdbcTemplate.execute("DELETE FROM desk_chair");
        jdbcTemplate.execute("DELETE FROM reading_area");

        area1 = createArea("A001", "第一阅览区");
        area2 = createArea("A002", "第二阅览区");

        // 分区1：DC001/DC002 启用，DC003 停用（在册含停用，仍要逐件巡检）；分区2：DC004
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

    private Long deskChairId(String code) {
        return deskChairMapper.findByAssetCode(code).getId();
    }

    private NightInspectionCreateRequest createRequest(Long areaId, String operator) {
        NightInspectionCreateRequest req = new NightInspectionCreateRequest();
        req.setAreaId(areaId);
        req.setOperator(operator);
        req.setRemark("闭馆后巡检");
        return req;
    }

    private NightInspectionCheckRequest checkRequest(String light, String socket, String deskSurface,
                                                     String handleOpinion, String operator) {
        NightInspectionCheckRequest req = new NightInspectionCheckRequest();
        req.setLightResult(light);
        req.setSocketResult(socket);
        req.setDeskSurfaceResult(deskSurface);
        req.setHandleOpinion(handleOpinion);
        req.setOperator(operator);
        return req;
    }

    private NightInspectionItem itemOf(NightInspectionBatch batch, String code) {
        return batch.getItems().stream()
                .filter(i -> code.equals(i.getAssetCode()))
                .findFirst().orElseThrow();
    }

    private NightInspectionBatch checkOne(NightInspectionBatch batch, String code,
                                          String light, String socket, String deskSurface,
                                          String handleOpinion) {
        NightInspectionItem item = itemOf(batch, code);
        return nightInspectionService.checkItem(batch.getId(), item.getId(),
                checkRequest(light, socket, deskSurface, handleOpinion, "张三"));
    }

    @Test
    void createShouldSnapshotAllBookedDesksIncludingDisabled() {
        NightInspectionBatch batch = nightInspectionService.createBatch(createRequest(area1, "张三"));

        assertEquals(3, batch.getTotalCount());
        assertEquals(0, batch.getCheckedCount());
        assertEquals(0, batch.getProblemCount());
        assertEquals(NightInspectionBatch.STATUS_OPEN, batch.getStatus());
        assertEquals(3, batch.getItems().size());
        assertTrue(batch.getItems().stream()
                .allMatch(i -> NightInspectionItem.CHECK_PENDING.equals(i.getCheckStatus())));
        assertTrue(batch.getItems().stream().anyMatch(i -> "DC003".equals(i.getAssetCode())));
    }

    @Test
    void createShouldBlockWhenAreaHasOpenBatch() {
        nightInspectionService.createBatch(createRequest(area1, "张三"));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> nightInspectionService.createBatch(createRequest(area1, "李四")));
        assertTrue(ex.getMessage().contains("巡检中的批次"));

        // 其他分区不受影响
        NightInspectionBatch other = nightInspectionService.createBatch(createRequest(area2, "李四"));
        assertEquals(1, other.getTotalCount());
    }

    @Test
    void createShouldRejectInvalidRequest() {
        assertThrows(IllegalArgumentException.class,
                () -> nightInspectionService.createBatch(createRequest(null, "张三")));
        assertThrows(IllegalArgumentException.class,
                () -> nightInspectionService.createBatch(createRequest(99999L, "张三")));
        assertThrows(IllegalArgumentException.class,
                () -> nightInspectionService.createBatch(createRequest(area1, "  ")));

        // 分区没有在册桌椅时不允许开批
        Long emptyArea = createArea("C001", "空阅览区");
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> nightInspectionService.createBatch(createRequest(emptyArea, "张三")));
        assertTrue(ex.getMessage().contains("没有在册桌椅"));
    }

    @Test
    void checkShouldRejectMissingLightOrSocketResult() {
        NightInspectionBatch batch = nightInspectionService.createBatch(createRequest(area1, "张三"));
        NightInspectionItem item = itemOf(batch, "DC001");

        // 没写灯结果
        IllegalArgumentException noLight = assertThrows(IllegalArgumentException.class,
                () -> nightInspectionService.checkItem(batch.getId(), item.getId(),
                        checkRequest(null, "NORMAL", "NORMAL", null, "张三")));
        assertTrue(noLight.getMessage().contains("灯"));
        assertTrue(noLight.getMessage().contains("不能提交该件"));

        // 没写插座结果
        IllegalArgumentException noSocket = assertThrows(IllegalArgumentException.class,
                () -> nightInspectionService.checkItem(batch.getId(), item.getId(),
                        checkRequest("NORMAL", "  ", "NORMAL", null, "张三")));
        assertTrue(noSocket.getMessage().contains("插座"));

        // 没写桌面结果
        IllegalArgumentException noDesk = assertThrows(IllegalArgumentException.class,
                () -> nightInspectionService.checkItem(batch.getId(), item.getId(),
                        checkRequest("NORMAL", "NORMAL", null, null, "张三")));
        assertTrue(noDesk.getMessage().contains("桌面"));

        // 非法结果值
        assertThrows(IllegalArgumentException.class,
                () -> nightInspectionService.checkItem(batch.getId(), item.getId(),
                        checkRequest("OK", "NORMAL", "NORMAL", null, "张三")));

        // 全部拦截后该件仍是待巡
        NightInspectionBatch fresh = nightInspectionService.findBatchById(batch.getId());
        assertEquals(NightInspectionItem.CHECK_PENDING, itemOf(fresh, "DC001").getCheckStatus());
        assertEquals(0, fresh.getCheckedCount());
    }

    @Test
    void checkShouldRequireHandleOpinionWhenProblemFound() {
        NightInspectionBatch batch = nightInspectionService.createBatch(createRequest(area1, "张三"));
        NightInspectionItem item = itemOf(batch, "DC001");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> nightInspectionService.checkItem(batch.getId(), item.getId(),
                        checkRequest("ABNORMAL", "NORMAL", "NORMAL", null, "张三")));
        assertTrue(ex.getMessage().contains("处理意见"));

        // 全部正常时不要求处理意见
        NightInspectionBatch after = nightInspectionService.checkItem(batch.getId(), item.getId(),
                checkRequest("NORMAL", "NORMAL", "NORMAL", null, "张三"));
        NightInspectionItem checked = itemOf(after, "DC001");
        assertEquals(NightInspectionItem.CHECK_CHECKED, checked.getCheckStatus());
        assertEquals(0, checked.getHasProblem());
        assertEquals(1, after.getCheckedCount());
        assertEquals(0, after.getProblemCount());
    }

    @Test
    void checkShouldMarkProblemAndCountIt() {
        NightInspectionBatch batch = nightInspectionService.createBatch(createRequest(area1, "张三"));

        NightInspectionBatch after = checkOne(batch, "DC002", "NORMAL", "ABNORMAL", "NORMAL",
                "插座失灵，已报修并暂停使用该座位");
        NightInspectionItem item = itemOf(after, "DC002");
        assertEquals(1, item.getHasProblem());
        assertEquals("插座失灵，已报修并暂停使用该座位", item.getHandleOpinion());
        assertEquals("张三", item.getCheckedBy());
        assertNotNull(item.getCheckedAt());
        assertEquals(1, after.getCheckedCount());
        assertEquals(1, after.getProblemCount());

        // 同一批内改登记（巡完前允许更正），问题数跟着重算
        NightInspectionBatch corrected = checkOne(after, "DC002", "NORMAL", "NORMAL", "NORMAL", null);
        assertEquals(1, corrected.getCheckedCount());
        assertEquals(0, corrected.getProblemCount());
        assertEquals(0, itemOf(corrected, "DC002").getHasProblem());
    }

    @Test
    void completeShouldBlockUntilAllItemsChecked() {
        NightInspectionBatch batch = nightInspectionService.createBatch(createRequest(area1, "张三"));
        batch = checkOne(batch, "DC001", "NORMAL", "NORMAL", "NORMAL", null);

        NightInspectionBatch current = batch;
        final Long openBatchId = current.getId();
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> nightInspectionService.completeBatch(openBatchId, handle("张三")));
        assertTrue(ex.getMessage().contains("未巡检"));

        current = checkOne(current, "DC002", "ABNORMAL", "NORMAL", "NORMAL", "灯管不亮，已登记报修");
        current = checkOne(current, "DC003", "NORMAL", "NORMAL", "ABNORMAL", "桌面涂鸦，已安排清理");
        NightInspectionBatch done = nightInspectionService.completeBatch(current.getId(), handle("张三"));

        assertEquals(NightInspectionBatch.STATUS_COMPLETED, done.getStatus());
        assertNotNull(done.getCompletedAt());
        assertEquals(3, done.getCheckedCount());
        assertEquals(2, done.getProblemCount());

        // 结束后禁止再登记
        NightInspectionItem anyItem = itemOf(done, "DC001");
        Long doneId = done.getId();
        assertThrows(IllegalArgumentException.class,
                () -> nightInspectionService.checkItem(doneId, anyItem.getId(),
                        checkRequest("NORMAL", "NORMAL", "NORMAL", null, "张三")));
        assertThrows(IllegalArgumentException.class,
                () -> nightInspectionService.completeBatch(doneId, handle("张三")));
    }

    @Test
    void recordsShouldFilterByAreaAndHasProblem() {
        NightInspectionBatch batch1 = nightInspectionService.createBatch(createRequest(area1, "张三"));
        batch1 = checkOne(batch1, "DC001", "ABNORMAL", "NORMAL", "NORMAL", "灯不亮，已报修");
        batch1 = checkOne(batch1, "DC002", "NORMAL", "NORMAL", "NORMAL", null);
        checkOne(batch1, "DC003", "NORMAL", "NORMAL", "NORMAL", null);

        NightInspectionBatch batch2 = nightInspectionService.createBatch(createRequest(area2, "李四"));
        checkOne(batch2, "DC004", "NORMAL", "ABNORMAL", "NORMAL", "插座失灵，已贴停用条");

        // 全部记录：4 件已巡
        assertEquals(4, nightInspectionService.searchRecords(null, null).size());
        // 按分区筛
        List<NightInspectionItem> area1Records = nightInspectionService.searchRecords(area1, null);
        assertEquals(3, area1Records.size());
        assertTrue(area1Records.stream().allMatch(i -> area1.equals(i.getAreaId())));
        // 按是否有问题筛
        List<NightInspectionItem> problems = nightInspectionService.searchRecords(null, 1);
        assertEquals(2, problems.size());
        assertTrue(problems.stream().allMatch(i -> Integer.valueOf(1).equals(i.getHasProblem())));
        // 分区 + 是否有问题组合筛
        List<NightInspectionItem> area1Problems = nightInspectionService.searchRecords(area1, 1);
        assertEquals(1, area1Problems.size());
        assertEquals("DC001", area1Problems.get(0).getAssetCode());
        // 无问题筛
        assertEquals(2, nightInspectionService.searchRecords(null, 0).size());
    }

    @Test
    void recordsByDeskChairShouldSpanBatches() {
        // 第一晚：DC001 灯不亮
        NightInspectionBatch night1 = nightInspectionService.createBatch(createRequest(area1, "张三"));
        night1 = checkOne(night1, "DC001", "ABNORMAL", "NORMAL", "NORMAL", "灯不亮，已报修");
        night1 = checkOne(night1, "DC002", "NORMAL", "NORMAL", "NORMAL", null);
        night1 = checkOne(night1, "DC003", "NORMAL", "NORMAL", "NORMAL", null);
        nightInspectionService.completeBatch(night1.getId(), handle("张三"));

        // 第二晚：同一件 DC001 复检正常
        NightInspectionBatch night2 = nightInspectionService.createBatch(createRequest(area1, "李四"));
        night2 = checkOne(night2, "DC001", "NORMAL", "NORMAL", "NORMAL", null);
        night2 = checkOne(night2, "DC002", "NORMAL", "NORMAL", "NORMAL", null);
        checkOne(night2, "DC003", "NORMAL", "NORMAL", "NORMAL", null);

        List<NightInspectionItem> history =
                nightInspectionService.findRecordsByDeskChair(deskChairId("DC001"));
        assertEquals(2, history.size());
        assertEquals(2, history.stream().map(NightInspectionItem::getBatchNo).distinct().count());
        // 新的在前
        assertEquals("NORMAL", history.get(0).getLightResult());
        assertEquals("ABNORMAL", history.get(1).getLightResult());

        assertThrows(IllegalArgumentException.class,
                () -> nightInspectionService.findRecordsByDeskChair(99999L));
    }

    private NightInspectionHandleRequest handle(String operator) {
        NightInspectionHandleRequest req = new NightInspectionHandleRequest();
        req.setOperator(operator);
        return req;
    }
}
