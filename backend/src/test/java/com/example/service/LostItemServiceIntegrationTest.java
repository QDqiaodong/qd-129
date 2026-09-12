package com.example.service;

import com.example.TestRedisConfig;
import com.example.dto.LostItemClaimRequest;
import com.example.dto.LostItemCreateRequest;
import com.example.dto.SeatHoldCreateRequest;
import com.example.dto.SeatHoldHoldRequest;
import com.example.entity.DeskChair;
import com.example.entity.LostItem;
import com.example.entity.ReadingArea;
import com.example.entity.SeatHoldBatch;
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
class LostItemServiceIntegrationTest {

    @Autowired
    private LostItemService lostItemService;

    @Autowired
    private SeatHoldService seatHoldService;

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
        jdbcTemplate.execute("DELETE FROM lost_item");
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
        createDeskChair("DC003", area2, 1);
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

    private LostItemCreateRequest createRequest(Long areaId, String assetCode) {
        LostItemCreateRequest req = new LostItemCreateRequest();
        req.setAreaId(areaId);
        req.setDeskChairId(deskId(assetCode));
        req.setItemName("黑色双肩包");
        req.setStorageLocation("服务台抽屉 3 号");
        req.setRemark("闭馆清场时在桌下发现");
        req.setOperator("值班员甲");
        return req;
    }

    private LostItemClaimRequest claimRequest() {
        LostItemClaimRequest req = new LostItemClaimRequest();
        req.setClaimerName("张三");
        req.setClaimerVerify("学生证 20230101，核对包内校园卡一致");
        req.setClaimConclusion("核验通过，物品完好交还领取人");
        req.setOperator("值班员乙");
        return req;
    }

    @Test
    void createShouldRegisterPendingItemWithSnapshots() {
        LostItem item = lostItemService.create(createRequest(area1, "DC001"));

        assertTrue(item.getItemNo().startsWith("YW"));
        assertEquals(LostItem.STATUS_PENDING, item.getStatus());
        assertEquals(area1, item.getAreaId());
        assertEquals("第一阅览区", item.getAreaName());
        assertEquals("DC001", item.getAssetCode());
        assertEquals("黑色双肩包", item.getItemName());
        assertEquals("服务台抽屉 3 号", item.getStorageLocation());
        assertEquals("值班员甲", item.getFoundBy());
        assertNotNull(item.getCreatedAt());
        // 登记遗失不改桌椅状态，拦截在开高峰占座时生效
        assertEquals(1, deskChairMapper.findByAssetCode("DC001").getStatus());
    }

    @Test
    void createShouldRejectMissingFieldsAndCrossAreaDesk() {
        LostItemCreateRequest noArea = createRequest(null, "DC001");
        assertThrows(IllegalArgumentException.class, () -> lostItemService.create(noArea));

        LostItemCreateRequest noName = createRequest(area1, "DC001");
        noName.setItemName("  ");
        assertThrows(IllegalArgumentException.class, () -> lostItemService.create(noName));

        LostItemCreateRequest noStorage = createRequest(area1, "DC001");
        noStorage.setStorageLocation(null);
        assertThrows(IllegalArgumentException.class, () -> lostItemService.create(noStorage));

        LostItemCreateRequest noOperator = createRequest(area1, "DC001");
        noOperator.setOperator(" ");
        assertThrows(IllegalArgumentException.class, () -> lostItemService.create(noOperator));

        // DC003 属于分区2，不能登记到分区1
        LostItemCreateRequest crossArea = createRequest(area1, "DC003");
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> lostItemService.create(crossArea));
        assertTrue(ex.getMessage().contains("分区"));
    }

    @Test
    void claimShouldRequireClaimerVerificationAndConclusion() {
        LostItem item = lostItemService.create(createRequest(area1, "DC001"));

        LostItemClaimRequest noClaimer = claimRequest();
        noClaimer.setClaimerName(" ");
        assertThrows(IllegalArgumentException.class, () -> lostItemService.claim(item.getId(), noClaimer));

        LostItemClaimRequest noVerify = claimRequest();
        noVerify.setClaimerVerify(null);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> lostItemService.claim(item.getId(), noVerify));
        assertTrue(ex.getMessage().contains("核验"));

        LostItemClaimRequest noConclusion = claimRequest();
        noConclusion.setClaimConclusion("  ");
        assertThrows(IllegalArgumentException.class, () -> lostItemService.claim(item.getId(), noConclusion));

        LostItemClaimRequest noOperator = claimRequest();
        noOperator.setOperator(null);
        assertThrows(IllegalArgumentException.class, () -> lostItemService.claim(item.getId(), noOperator));

        // 全部校验失败后单据仍待领取
        assertEquals(LostItem.STATUS_PENDING, lostItemService.findById(item.getId()).getStatus());
    }

    @Test
    void claimShouldCloseItemAndPendingFlagSurvivesReload() {
        LostItem item = lostItemService.create(createRequest(area1, "DC001"));

        // 刷新（重新查询）后待领标记仍在
        LostItem reloaded = lostItemService.findById(item.getId());
        assertEquals(LostItem.STATUS_PENDING, reloaded.getStatus());
        assertEquals(1, lostItemService.search(area1, LostItem.STATUS_PENDING).size());

        LostItem claimed = lostItemService.claim(item.getId(), claimRequest());
        assertEquals(LostItem.STATUS_CLAIMED, claimed.getStatus());
        assertEquals("张三", claimed.getClaimerName());
        assertNotNull(claimed.getClaimerVerify());
        assertNotNull(claimed.getClaimConclusion());
        assertEquals("值班员乙", claimed.getClaimedBy());
        assertNotNull(claimed.getClaimedAt());

        // 领取闭环后不再出现在待领列表，重复领取被拒绝
        assertEquals(0, lostItemService.search(area1, LostItem.STATUS_PENDING).size());
        assertThrows(IllegalArgumentException.class,
                () -> lostItemService.claim(item.getId(), claimRequest()));
    }

    @Test
    void searchShouldFilterByAreaAndPendingStatus() {
        lostItemService.create(createRequest(area1, "DC001"));
        LostItem claimed = lostItemService.create(createRequest(area1, "DC002"));
        lostItemService.claim(claimed.getId(), claimRequest());
        lostItemService.create(createRequest(area2, "DC003"));

        assertEquals(3, lostItemService.search(null, null).size());
        assertEquals(2, lostItemService.search(area1, null).size());
        assertEquals(1, lostItemService.search(area1, LostItem.STATUS_PENDING).size());
        assertEquals(1, lostItemService.search(area1, LostItem.STATUS_CLAIMED).size());
        assertEquals(1, lostItemService.search(area2, LostItem.STATUS_PENDING).size());
        assertEquals(0, lostItemService.search(area2, LostItem.STATUS_CLAIMED).size());
        assertThrows(IllegalArgumentException.class, () -> lostItemService.search(null, "BOGUS"));
    }

    @Test
    void seatHoldShouldBeBlockedByPendingLostItemWithClearReason() {
        LostItem item = lostItemService.create(createRequest(area1, "DC001"));

        SeatHoldCreateRequest holdReq = new SeatHoldCreateRequest();
        holdReq.setAreaId(area1);
        holdReq.setTimeSlot("08:00-11:30 早高峰");
        holdReq.setDeskChairIds(List.of(deskId("DC001")));
        holdReq.setOperator("值班员丙");

        // 待领取桌椅开批被拦，原因含遗失单号和物品名称
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> seatHoldService.createBatch(holdReq));
        assertTrue(ex.getMessage().contains(item.getItemNo()));
        assertTrue(ex.getMessage().contains("黑色双肩包"));
        assertTrue(ex.getMessage().contains("不能开高峰占座"));
        // 拦截不改桌椅状态
        assertEquals(1, deskChairMapper.findByAssetCode("DC001").getStatus());

        // 进行中批次追加占住同样被拦
        SeatHoldCreateRequest otherReq = new SeatHoldCreateRequest();
        otherReq.setAreaId(area1);
        otherReq.setTimeSlot("08:00-11:30 早高峰");
        otherReq.setDeskChairIds(List.of(deskId("DC002")));
        otherReq.setOperator("值班员丙");
        SeatHoldBatch openBatch = seatHoldService.createBatch(otherReq);
        SeatHoldHoldRequest appendReq = new SeatHoldHoldRequest();
        appendReq.setDeskChairIds(List.of(deskId("DC001")));
        appendReq.setOperator("值班员丙");
        assertThrows(IllegalArgumentException.class,
                () -> seatHoldService.hold(openBatch.getId(), appendReq));

        // 领取闭环后可以正常开批占座
        lostItemService.claim(item.getId(), claimRequest());
        SeatHoldBatch batch = seatHoldService.createBatch(holdReq);
        assertEquals(SeatHoldBatch.STATUS_OPEN, batch.getStatus());
        assertEquals(1, batch.getHeldCount());
        assertEquals(0, deskChairMapper.findByAssetCode("DC001").getStatus());
    }

    @Test
    void pendingListByAreaShouldFeedSeatHoldPicker() {
        lostItemService.create(createRequest(area1, "DC001"));
        lostItemService.create(createRequest(area2, "DC003"));

        List<LostItem> pending = lostItemService.findPendingByArea(area1);
        assertEquals(1, pending.size());
        assertEquals("DC001", pending.get(0).getAssetCode());
        assertEquals(LostItem.STATUS_PENDING, pending.get(0).getStatus());
        assertThrows(IllegalArgumentException.class, () -> lostItemService.findPendingByArea(null));
    }
}
