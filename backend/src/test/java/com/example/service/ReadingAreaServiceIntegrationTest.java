package com.example.service;

import com.example.TestRedisConfig;
import com.example.entity.ReadingArea;
import com.example.mapper.ReadingAreaMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = "spring.sql.init.mode=always")
@Import(TestRedisConfig.class)
class ReadingAreaServiceIntegrationTest {

    @Autowired
    private ReadingAreaService readingAreaService;

    @Autowired
    private ReadingAreaMapper readingAreaMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long areaId;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("DELETE FROM reading_area");
        ReadingArea area = new ReadingArea();
        area.setAreaCode("A001");
        area.setAreaName("第一阅览区");
        area.setStatus(1);
        readingAreaMapper.insert(area);
        areaId = area.getId();
    }

    @Test
    void markClosedShouldPersistEndTimeAndClearShouldNullIt() {
        LocalDateTime closedUntil = LocalDateTime.now().plusHours(4);

        ReadingArea closed = readingAreaService.markClosed(areaId, closedUntil);
        assertNotNull(closed.getClosedUntil());
        assertTrue(closed.getClosedUntil().isAfter(LocalDateTime.now().plusHours(3)));

        ReadingArea reopened = readingAreaService.clearClosed(areaId);
        assertNull(reopened.getClosedUntil());
    }

    @Test
    void markClosedShouldRejectMissingPastOrUnknownArea() {
        assertThrows(IllegalArgumentException.class,
                () -> readingAreaService.markClosed(areaId, null));
        assertThrows(IllegalArgumentException.class,
                () -> readingAreaService.markClosed(areaId, LocalDateTime.now().minusMinutes(1)));
        assertThrows(IllegalArgumentException.class,
                () -> readingAreaService.markClosed(99999L, LocalDateTime.now().plusHours(1)));
        assertNull(readingAreaMapper.selectById(areaId).getClosedUntil(),
                "非法挂闭馆不得写入结束时刻");
    }

    @Test
    void updateAreaProfileShouldNotTouchClosedUntil() {
        readingAreaService.markClosed(areaId, LocalDateTime.now().plusHours(2));
        ReadingArea editing = readingAreaService.findById(areaId);
        editing.setAreaName("第一阅览区（更名）");
        readingAreaService.update(editing);

        assertNotNull(readingAreaMapper.selectById(areaId).getClosedUntil(),
                "普通资料编辑不得清掉已挂出的闭馆结束时刻");
    }
}
