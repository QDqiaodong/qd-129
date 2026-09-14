package com.example.controller;

import com.example.dto.NightInspectionCheckRequest;
import com.example.dto.NightInspectionCreateRequest;
import com.example.dto.NightInspectionHandleRequest;
import com.example.entity.NightInspectionBatch;
import com.example.entity.NightInspectionItem;
import com.example.service.NightInspectionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/night-inspection")
@CrossOrigin(origins = "*")
public class NightInspectionController {

    @Autowired
    private NightInspectionService nightInspectionService;

    @PostMapping("/batch")
    public ResponseEntity<NightInspectionBatch> createBatch(@RequestBody NightInspectionCreateRequest request) {
        return ResponseEntity.ok(nightInspectionService.createBatch(request));
    }

    @GetMapping("/batch")
    public ResponseEntity<List<NightInspectionBatch>> listBatches(@RequestParam(required = false) Long areaId,
                                                                  @RequestParam(required = false) String status) {
        return ResponseEntity.ok(nightInspectionService.listBatches(areaId, status));
    }

    @GetMapping("/batch/{id}")
    public ResponseEntity<NightInspectionBatch> getBatch(@PathVariable Long id) {
        return ResponseEntity.ok(nightInspectionService.findBatchById(id));
    }

    @PostMapping("/batch/{batchId}/item/{itemId}/check")
    public ResponseEntity<NightInspectionBatch> checkItem(@PathVariable Long batchId,
                                                          @PathVariable Long itemId,
                                                          @RequestBody NightInspectionCheckRequest request) {
        return ResponseEntity.ok(nightInspectionService.checkItem(batchId, itemId, request));
    }

    @PostMapping("/batch/{id}/complete")
    public ResponseEntity<NightInspectionBatch> completeBatch(@PathVariable Long id,
                                                              @RequestBody NightInspectionHandleRequest request) {
        return ResponseEntity.ok(nightInspectionService.completeBatch(id, request));
    }

    @GetMapping("/record")
    public ResponseEntity<List<NightInspectionItem>> searchRecords(@RequestParam(required = false) Long areaId,
                                                                   @RequestParam(required = false) Integer hasProblem) {
        return ResponseEntity.ok(nightInspectionService.searchRecords(areaId, hasProblem));
    }

    @GetMapping("/desk-chair/{deskChairId}/records")
    public ResponseEntity<List<NightInspectionItem>> recordsByDeskChair(@PathVariable Long deskChairId) {
        return ResponseEntity.ok(nightInspectionService.findRecordsByDeskChair(deskChairId));
    }
}
