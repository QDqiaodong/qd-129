package com.example.controller;

import com.example.dto.AreaCloseRequest;
import com.example.entity.ReadingArea;
import com.example.service.ReadingAreaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/reading-area")
@CrossOrigin(origins = "*")
public class ReadingAreaController {

    @Autowired
    private ReadingAreaService readingAreaService;

    @GetMapping
    public ResponseEntity<List<ReadingArea>> getAll() {
        return ResponseEntity.ok(readingAreaService.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ReadingArea> getById(@PathVariable Long id) {
        return ResponseEntity.ok(readingAreaService.findById(id));
    }

    @GetMapping("/code/{code}")
    public ResponseEntity<ReadingArea> getByCode(@PathVariable String code) {
        return ResponseEntity.ok(readingAreaService.findByCode(code));
    }

    @PostMapping
    public ResponseEntity<ReadingArea> create(@RequestBody ReadingArea readingArea) {
        return ResponseEntity.ok(readingAreaService.save(readingArea));
    }

    @PutMapping
    public ResponseEntity<ReadingArea> update(@RequestBody ReadingArea readingArea) {
        return ResponseEntity.ok(readingAreaService.update(readingArea));
    }

    /** 挂“今日闭馆”牌：登记闭馆结束时刻，结束时刻前高峰占座开批被拦截 */
    @PostMapping("/{id}/close")
    public ResponseEntity<ReadingArea> markClosed(@PathVariable Long id,
                                                  @RequestBody AreaCloseRequest request) {
        return ResponseEntity.ok(readingAreaService.markClosed(
                id, request == null ? null : request.getClosedUntil()));
    }

    /** 提前摘“今日闭馆”牌：分区即刻恢复可开占座（闭馆到期则无需操作，自动失效） */
    @DeleteMapping("/{id}/close")
    public ResponseEntity<ReadingArea> clearClosed(@PathVariable Long id) {
        return ResponseEntity.ok(readingAreaService.clearClosed(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        readingAreaService.deleteById(id);
        return ResponseEntity.ok().build();
    }
}