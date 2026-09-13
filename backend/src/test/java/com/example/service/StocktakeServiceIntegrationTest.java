package com.example.service;

import com.example.TestRedisConfig;
import com.example.dto.StocktakeActualLine;
import com.example.dto.StocktakeCreateRequest;
import com.example.dto.StocktakeHandleRequest;
import com.example.entity.DeskChair;
import com.example.entity.ReadingArea;
import com.example.entity.StocktakeBatch;
import com.example.entity.StocktakeHandleRecord;
import com.example.entity.StocktakeItem;
import com.example.mapper.DeskChairMapper;
import com.example.mapper.DeskChairTagMapper;
import com.example.mapper.ReadingAreaMapper;
import com.example.mapper.TagMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = "spring.sql.init.mode=always")
@Import(TestRedisConfig.class)
class StocktakeServiceIntegrationTest {

    @Autowired
    private StocktakeService stocktakeService;

    @Autowired
    private DeskChairMapper deskChairMapper;

    @Autowired
    private ReadingAreaMapper readingAreaMapper;

    @Autowired
    private TagMapper tagMapper;

    @Autowired
    private DeskChairTagMapper deskChairTagMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long area1;
    private Long area2;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("DELETE FROM stocktake_handle_record");
        jdbcTemplate.execute("DELETE FROM stocktake_item");
        jdbcTemplate.execute("DELETE FROM stocktake_batch");
        jdbcTemplate.execute("DELETE FROM desk_chair_tag");
        jdbcTemplate.execute("DELETE FROM desk_chair");
        jdbcTemplate.execute("DELETE FROM reading_area");
        jdbcTemplate.execute("DELETE FROM tag");

        area1 = createArea("A001", "第一阅览区");
        area2 = createArea("A002", "第二阅览区");

        // 分区1：DC001/DC002 正常启用；DC003 停用；分区2：DC004
        createDeskChair("DC001", area1, 1);
        createDeskChair("DC002", area1, 1);
        createDeskChair("DC003", area1, 0);
        createDeskChair("DC004", area2, 1);

        createTag("TAG001", "自习专用");
        createTag("TAG004", "双人桌");
        deskChairTagMapper.insertTag(deskChairId("DC001"), tagId("TAG001"));
        deskChairTagMapper.insertTag(deskChairId("DC001"), tagId("TAG004"));
        deskChairTagMapper.insertTag(deskChairId("DC002"), tagId("TAG001"));
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

    private void createTag(String code, String name) {
        com.example.entity.Tag tag = new com.example.entity.Tag();
        tag.setTagCode(code);
        tag.setTagName(name);
        tag.setTagColor("#409EFF");
        tag.setStatus(1);
        tagMapper.insert(tag);
    }

    private Long tagId(String code) {
        return tagMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.example.entity.Tag>()
                .eq(com.example.entity.Tag::getTagCode, code)).getId();
    }

    private Long deskChairId(String code) {
        return deskChairMapper.findByAssetCode(code).getId();
    }

    private StocktakeCreateRequest createRequest(Long areaId, String operator) {
        StocktakeCreateRequest req = new StocktakeCreateRequest();
        req.setAreaId(areaId);
        req.setRemark("期末盘点");
        req.setOperator(operator);
        return req;
    }

    private StocktakeActualLine line(String code, Long areaId, Integer status, List<Long> tagIds) {
        StocktakeActualLine l = new StocktakeActualLine();
        l.setAssetCode(code);
        l.setActualAreaId(areaId);
        l.setActualStatus(status);
        l.setActualTagIds(tagIds);
        return l;
    }

    private StocktakeHandleRequest handle(String opinion, String operator) {
        StocktakeHandleRequest req = new StocktakeHandleRequest();
        req.setHandleOpinion(opinion);
        req.setOperator(operator);
        return req;
    }

    private StocktakeHandleRequest handleMissing(String missingReason, String operator) {
        StocktakeHandleRequest req = new StocktakeHandleRequest();
        req.setMissingReason(missingReason);
        req.setOperator(operator);
        return req;
    }

    private StocktakeItem itemOf(StocktakeBatch batch, String code) {
        return batch.getItems().stream()
                .filter(i -> code.equals(i.getAssetCode()))
                .findFirst().orElseThrow();
    }

    @Test
    void createShouldBlockWhenAreaHasOpenBatch() {
        stocktakeService.createBatch(createRequest(area1, "张三"));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> stocktakeService.createBatch(createRequest(area1, "张三")));
        assertTrue(ex.getMessage().contains("盘点中"));
    }

    @Test
    void createShouldRejectMissingAreaOrOperator() {
        assertThrows(IllegalArgumentException.class,
                () -> stocktakeService.createBatch(createRequest(null, "张三")));
        assertThrows(IllegalArgumentException.class,
                () -> stocktakeService.createBatch(createRequest(99999L, "张三")));
        StocktakeCreateRequest req = createRequest(area1, "  ");
        assertThrows(IllegalArgumentException.class, () -> stocktakeService.createBatch(req));
    }

    @Test
    void submitShouldMarkMissingSurplusWrongAreaStatusAndTagDifferences() {
        StocktakeBatch batch = stocktakeService.createBatch(createRequest(area1, "张三"));

        List<StocktakeActualLine> lines = new ArrayList<>();
        Long tagStudy = tagId("TAG001");
        Long tagDouble = tagId("TAG004");
        // DC001：编号/分区/状态/标签全一致
        lines.add(line("DC001", area1, 1, List.of(tagStudy, tagDouble)));
        // DC002：标签不符（在册有自习专用，实盘录成双人桌）
        lines.add(line("DC002", area1, 1, List.of(tagDouble)));
        // DC003：在册停用，实盘为启用 => 停用不符
        lines.add(line("DC003", area1, 1, List.of()));
        // DC004：在册属分区2，盘到了分区1 => 错区
        lines.add(line("DC004", area1, 1, null));
        // DC999：在册不存在 => 盘盈
        lines.add(line("DC999", area1, 1, null));
        // 分区1 在册 DC001/DC002/DC003 都盘到了，这里无缺失；另加一个缺失场景在独立用例

        StocktakeBatch result = stocktakeService.submitActuals(batch.getId(), lines, "张三", "首轮");

        assertEquals(3, result.getExpectedCount());
        assertEquals(5, result.getActualCount());
        assertEquals(4, result.getDiffCount());
        assertEquals(0, result.getCheckedCount());
        assertEquals(StocktakeItem.DIFF_MATCH, itemOf(result, "DC001").getDiffType());
        assertEquals(StocktakeItem.DIFF_TAG_MISMATCH, itemOf(result, "DC002").getDiffType());
        assertEquals(StocktakeItem.DIFF_STATUS_MISMATCH, itemOf(result, "DC003").getDiffType());
        assertEquals(StocktakeItem.DIFF_WRONG_AREA, itemOf(result, "DC004").getDiffType());
        assertEquals(StocktakeItem.DIFF_SURPLUS, itemOf(result, "DC999").getDiffType());
        assertTrue(itemOf(result, "DC001").getDiffDetail().contains("一致"));

        // 批次级录入记录
        assertTrue(result.getRecords().stream()
                .anyMatch(r -> StocktakeHandleRecord.ACTION_SUBMIT.equals(r.getAction())
                        && r.getOpinion().contains("差异 4 项")));
    }

    @Test
    void submitShouldMarkMissingWhenBookedAssetNotCounted() {
        StocktakeBatch batch = stocktakeService.createBatch(createRequest(area1, "张三"));
        // 只盘到 DC001，DC002（启用）、DC003（停用）均缺失
        StocktakeBatch result = stocktakeService.submitActuals(
                batch.getId(), List.of(line("DC001", area1, 1, null)), "张三", null);

        assertEquals(3, result.getExpectedCount());
        assertEquals(1, result.getActualCount());
        assertEquals(2, result.getDiffCount());
        StocktakeItem missing2 = itemOf(result, "DC002");
        StocktakeItem missing3 = itemOf(result, "DC003");
        assertEquals(StocktakeItem.DIFF_MISSING, missing2.getDiffType());
        assertEquals(StocktakeItem.DIFF_MISSING, missing3.getDiffType());
        assertEquals(area1, missing2.getBookAreaId());
        assertEquals(0, missing3.getBookStatus());
        assertNull(missing2.getActualAreaId());
    }

    @Test
    void submitShouldRejectDuplicateCodesWithinOnePayload() {
        StocktakeBatch batch = stocktakeService.createBatch(createRequest(area1, "张三"));
        List<StocktakeActualLine> lines = List.of(
                line("DC001", area1, 1, null),
                line(" DC001 ", area1, 1, null));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> stocktakeService.submitActuals(batch.getId(), lines, "张三", null));
        assertTrue(ex.getMessage().contains("DC001"));
        // 被拒绝后批次仍无明细
        assertEquals(0, stocktakeService.findById(batch.getId()).getItems().size());
    }

    @Test
    void submitShouldBlockOnCompletedBatch() {
        StocktakeBatch batch = stocktakeService.createBatch(createRequest(area1, "张三"));
        stocktakeService.submitActuals(batch.getId(),
                List.of(line("DC001", area1, 1, null)), "张三", null);
        stocktakeService.confirmItem(batch.getId(),
                itemOf(stocktakeService.findById(batch.getId()), "DC001").getId(),
                handle("一致无误", "张三"));
        // DC002/DC003 缺失项也需写明缺失原因后确认
        StocktakeBatch fresh = stocktakeService.findById(batch.getId());
        fresh.getItems().stream()
                .filter(i -> StocktakeItem.CHECK_PENDING.equals(i.getCheckStatus()))
                .forEach(i -> stocktakeService.confirmItem(batch.getId(), i.getId(),
                        StocktakeItem.DIFF_MISSING.equals(i.getDiffType())
                                ? handleMissing("搬至维修间，暂未归位", "张三")
                                : handle("记录缺失", "张三")));
        stocktakeService.completeBatch(batch.getId(), handle(null, "张三"));

        assertThrows(IllegalArgumentException.class, () -> stocktakeService.submitActuals(
                batch.getId(), List.of(line("DC001", area1, 1, null)), "张三", null));
    }

    @Test
    void confirmShouldRequireOpinionAndBlockDoubleConfirm() {
        StocktakeBatch batch = stocktakeService.createBatch(createRequest(area1, "张三"));
        stocktakeService.submitActuals(batch.getId(),
                List.of(line("DC001", area1, 1, null)), "张三", null);
        Long itemId = itemOf(stocktakeService.findById(batch.getId()), "DC001").getId();

        assertThrows(IllegalArgumentException.class,
                () -> stocktakeService.confirmItem(batch.getId(), itemId, handle("  ", "张三")));

        StocktakeBatch confirmed = stocktakeService.confirmItem(
                batch.getId(), itemId, handle("账实一致", "李四"));
        StocktakeItem item = itemOf(confirmed, "DC001");
        assertEquals(StocktakeItem.CHECK_CONFIRMED, item.getCheckStatus());
        assertEquals("李四", item.getConfirmedBy());
        assertEquals(1, confirmed.getCheckedCount());

        // 重复确认被阻止
        assertThrows(IllegalArgumentException.class,
                () -> stocktakeService.confirmItem(batch.getId(), itemId, handle("再次确认", "李四")));
    }

    @Test
    void missingItemShouldRequireMissingReasonOnConfirmAndRetainIt() {
        StocktakeBatch batch = stocktakeService.createBatch(createRequest(area1, "张三"));
        stocktakeService.submitActuals(batch.getId(),
                List.of(line("DC001", area1, 1, null)), "张三", null);
        StocktakeItem missing = itemOf(stocktakeService.findById(batch.getId()), "DC002");
        assertEquals(StocktakeItem.DIFF_MISSING, missing.getDiffType());

        // 未填写缺失原因：确认被拦
        assertThrows(IllegalArgumentException.class,
                () -> stocktakeService.confirmItem(batch.getId(), missing.getId(),
                        handleMissing("  ", "张三")));

        StocktakeBatch confirmed = stocktakeService.confirmItem(
                batch.getId(), missing.getId(), handleMissing("借出维修暂未归还", "李四"));
        StocktakeItem item = itemOf(confirmed, "DC002");
        assertEquals(StocktakeItem.CHECK_CONFIRMED, item.getCheckStatus());
        assertEquals("借出维修暂未归还", item.getMissingReason());
        assertTrue(item.getHandleOpinion().contains("借出维修暂未归还"));
        // 处理记录可追溯到缺失原因
        assertTrue(item.getRecords().stream()
                .anyMatch(r -> StocktakeHandleRecord.ACTION_CONFIRM.equals(r.getAction())
                        && r.getOpinion().contains("借出维修暂未归还")));

        // 重新复核后缺失原因与确认意见一并清空，需重新写明
        StocktakeBatch rechecked = stocktakeService.recheckItem(
                batch.getId(), item.getId(), handle("现场再找一遍", "王五"));
        StocktakeItem recheckItem = itemOf(rechecked, "DC002");
        assertNull(recheckItem.getMissingReason());
        assertNull(recheckItem.getHandleOpinion());
        assertThrows(IllegalArgumentException.class,
                () -> stocktakeService.completeBatch(batch.getId(), handle(null, "张三")));
    }

    @Test
    void completeShouldListAssetsWhoseMissingReasonIsAbsent() {
        StocktakeBatch batch = stocktakeService.createBatch(createRequest(area1, "张三"));
        stocktakeService.submitActuals(batch.getId(),
                List.of(line("DC001", area1, 1, null)), "张三", null);
        StocktakeBatch fresh = stocktakeService.findById(batch.getId());
        for (StocktakeItem i : fresh.getItems()) {
            if (StocktakeItem.DIFF_MISSING.equals(i.getDiffType())) {
                stocktakeService.confirmItem(batch.getId(), i.getId(),
                        handleMissing("搬离现场", "张三"));
            } else {
                stocktakeService.confirmItem(batch.getId(), i.getId(), handle("账实一致", "张三"));
            }
        }

        // 模拟缺失原因丢失（数据异常/历史数据）：完成时必须拦住并点名资产编号
        jdbcTemplate.update("UPDATE stocktake_item SET missing_reason = NULL WHERE asset_code = 'DC002'");
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> stocktakeService.completeBatch(batch.getId(), handle(null, "张三")));
        assertTrue(ex.getMessage().contains("缺失原因"));
        assertTrue(ex.getMessage().contains("DC002"));
        assertFalse(ex.getMessage().contains("DC003"));

        jdbcTemplate.update("UPDATE stocktake_item SET missing_reason = '借出未归还' WHERE asset_code = 'DC002'");
        StocktakeBatch completed = stocktakeService.completeBatch(batch.getId(), handle(null, "张三"));
        assertEquals(StocktakeBatch.STATUS_COMPLETED, completed.getStatus());
    }

    @Test
    void recheckShouldResetItemAndAppendRecord() {
        StocktakeBatch batch = stocktakeService.createBatch(createRequest(area1, "张三"));
        stocktakeService.submitActuals(batch.getId(),
                List.of(line("DC001", area1, 1, null)), "张三", null);
        Long itemId = itemOf(stocktakeService.findById(batch.getId()), "DC001").getId();
        stocktakeService.confirmItem(batch.getId(), itemId, handle("账实一致", "李四"));

        StocktakeBatch rechecked = stocktakeService.recheckItem(
                batch.getId(), itemId, handle("现场再核一次", "王五"));
        StocktakeItem item = itemOf(rechecked, "DC001");
        assertEquals(StocktakeItem.CHECK_PENDING, item.getCheckStatus());
        assertEquals(1, item.getRecheckCount());
        assertNull(item.getHandleOpinion());
        assertNull(item.getConfirmedBy());
        assertEquals(0, rechecked.getCheckedCount());

        // 处理记录时间线：确认 + 复核均保留，原确认意见可追溯
        List<StocktakeHandleRecord> records = item.getRecords();
        assertEquals(2, records.size());
        assertEquals(StocktakeHandleRecord.ACTION_CONFIRM, records.get(0).getAction());
        assertEquals("账实一致", records.get(0).getOpinion());
        assertEquals(StocktakeHandleRecord.ACTION_RECHECK, records.get(1).getAction());

        // 待核状态不可再复核
        assertThrows(IllegalArgumentException.class,
                () -> stocktakeService.recheckItem(batch.getId(), itemId, handle(null, "王五")));
    }

    @Test
    void completeShouldBlockWhenPendingItemsExistAndSucceedAfterAllConfirmed() {
        StocktakeBatch batch = stocktakeService.createBatch(createRequest(area1, "张三"));
        stocktakeService.submitActuals(batch.getId(),
                List.of(line("DC001", area1, 1, null)), "张三", null);

        // 还有 DC002/DC003 缺失待核
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> stocktakeService.completeBatch(batch.getId(), handle(null, "张三")));
        assertTrue(ex.getMessage().contains("待核"));

        stocktakeService.findById(batch.getId()).getItems()
                .forEach(i -> stocktakeService.confirmItem(batch.getId(), i.getId(),
                        StocktakeItem.DIFF_MATCH.equals(i.getDiffType()) ? handle("账实一致", "张三")
                                : handleMissing("记录缺失并报修", "张三")));

        StocktakeBatch completed = stocktakeService.completeBatch(
                batch.getId(), handle("盘点闭环", "张三"));
        assertEquals(StocktakeBatch.STATUS_COMPLETED, completed.getStatus());
        assertEquals(3, completed.getCheckedCount());
        assertNotNull(completed.getCompletedAt());
        assertTrue(completed.getRecords().stream()
                .anyMatch(r -> StocktakeHandleRecord.ACTION_COMPLETE.equals(r.getAction())));

        // 已完成批次禁止确认/复核/再完成
        Long anyItemId = completed.getItems().get(0).getId();
        assertThrows(IllegalArgumentException.class,
                () -> stocktakeService.confirmItem(batch.getId(), anyItemId, handle("x", "张三")));
        assertThrows(IllegalArgumentException.class,
                () -> stocktakeService.recheckItem(batch.getId(), anyItemId, handle("x", "张三")));
        assertThrows(IllegalArgumentException.class,
                () -> stocktakeService.completeBatch(batch.getId(), handle(null, "张三")));

        // 已完成后同分区可以发起新盘点
        StocktakeBatch next = stocktakeService.createBatch(createRequest(area1, "张三"));
        assertEquals(StocktakeBatch.STATUS_OPEN, next.getStatus());
    }

    @Test
    void resubmitActualsShouldReplaceItemsAndKeepBatchSubmitTrail() {
        StocktakeBatch batch = stocktakeService.createBatch(createRequest(area1, "张三"));
        stocktakeService.submitActuals(batch.getId(),
                List.of(line("DC001", area1, 1, null)), "张三", "首轮");
        Long firstItemId = itemOf(stocktakeService.findById(batch.getId()), "DC001").getId();
        stocktakeService.confirmItem(batch.getId(), firstItemId, handle("首轮意见", "张三"));

        StocktakeBatch again = stocktakeService.submitActuals(
                batch.getId(), List.of(line("DC001", area1, 1, List.of(tagId("TAG001"), tagId("TAG004")))), "张三", "补录标签");

        // 旧明细及逐项确认被替换，已核数量归零，但两次批次级录入轨迹都保留
        assertEquals(0, again.getCheckedCount());
        assertEquals(2, again.getRecords().stream()
                .filter(r -> StocktakeHandleRecord.ACTION_SUBMIT.equals(r.getAction())).count());
        assertTrue(again.getItems().stream().noneMatch(i -> "首轮意见".equals(i.getHandleOpinion())));
        assertEquals(tagId("TAG001") + "," + tagId("TAG004"), itemOf(again, "DC001").getActualTagIds());
    }
}
