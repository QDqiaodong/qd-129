package com.example.controller;

import com.example.dto.SeatHoldCreateRequest;
import com.example.dto.SeatHoldHandleRequest;
import com.example.dto.SeatHoldHoldRequest;
import com.example.entity.SeatHoldBatch;
import com.example.entity.SeatHoldItem;
import com.example.service.SeatHoldService;
import com.example.vo.SeatHoldClearingVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/seat-hold")
@CrossOrigin(origins = "*")
public class SeatHoldController {

    @Autowired
    private SeatHoldService seatHoldService;

    @PostMapping("/batch")
    public ResponseEntity<SeatHoldBatch> createBatch(@RequestBody SeatHoldCreateRequest request) {
        return ResponseEntity.ok(seatHoldService.createBatch(request));
    }

    @GetMapping("/batch")
    public ResponseEntity<List<SeatHoldBatch>> listBatches(@RequestParam(required = false) Long areaId,
                                                           @RequestParam(required = false) String status) {
        return ResponseEntity.ok(seatHoldService.search(areaId, status));
    }

    @GetMapping("/active")
    public ResponseEntity<List<SeatHoldItem>> listActiveHolds(@RequestParam(required = false) Long areaId) {
        return ResponseEntity.ok(seatHoldService.findOpenHolds(areaId));
    }

    @GetMapping("/batch/{id}")
    public ResponseEntity<SeatHoldBatch> getBatch(@PathVariable Long id) {
        return ResponseEntity.ok(seatHoldService.findById(id));
    }

    @GetMapping("/batch/{id}/clearing")
    public ResponseEntity<SeatHoldClearingVO> getClearing(@PathVariable Long id) {
        return ResponseEntity.ok(seatHoldService.getClearing(id));
    }

    @PostMapping("/batch/{id}/hold")
    public ResponseEntity<SeatHoldBatch> hold(@PathVariable Long id,
                                              @RequestBody SeatHoldHoldRequest request) {
        return ResponseEntity.ok(seatHoldService.hold(id, request));
    }

    @PostMapping("/batch/{batchId}/item/{itemId}/release")
    public ResponseEntity<SeatHoldBatch> release(@PathVariable Long batchId,
                                                 @PathVariable Long itemId,
                                                 @RequestBody SeatHoldHandleRequest request) {
        return ResponseEntity.ok(seatHoldService.release(batchId, itemId, request));
    }

    @PostMapping("/batch/{batchId}/item/{itemId}/timeout")
    public ResponseEntity<SeatHoldBatch> markTimeout(@PathVariable Long batchId,
                                                     @PathVariable Long itemId,
                                                     @RequestBody SeatHoldHandleRequest request) {
        return ResponseEntity.ok(seatHoldService.markTimeout(batchId, itemId, request));
    }

    @PostMapping("/batch/{batchId}/item/{itemId}/revert-timeout")
    public ResponseEntity<SeatHoldBatch> revertTimeout(@PathVariable Long batchId,
                                                       @PathVariable Long itemId,
                                                       @RequestBody SeatHoldHandleRequest request) {
        return ResponseEntity.ok(seatHoldService.revertTimeout(batchId, itemId, request));
    }

    @PostMapping("/batch/{id}/finish")
    public ResponseEntity<SeatHoldBatch> finishBatch(@PathVariable Long id,
                                                     @RequestBody SeatHoldHandleRequest request) {
        return ResponseEntity.ok(seatHoldService.finishBatch(id, request));
    }

    @PostMapping("/batch/{batchId}/item/{itemId}/release-legacy")
    public ResponseEntity<SeatHoldBatch> releaseLegacy(@PathVariable Long batchId,
                                                       @PathVariable Long itemId,
                                                       @RequestBody SeatHoldHandleRequest request) {
        return ResponseEntity.ok(seatHoldService.releaseLegacy(batchId, itemId, request));
    }
}
