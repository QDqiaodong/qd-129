package com.example.controller;

import com.example.dto.RepairOrderCreateRequest;
import com.example.dto.RepairOrderHandleRequest;
import com.example.entity.RepairOrder;
import com.example.service.RepairOrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/repair-order")
@CrossOrigin(origins = "*")
public class RepairOrderController {

    @Autowired
    private RepairOrderService repairOrderService;

    @PostMapping
    public ResponseEntity<RepairOrder> create(@RequestBody RepairOrderCreateRequest request) {
        return ResponseEntity.ok(repairOrderService.create(request));
    }

    @GetMapping
    public ResponseEntity<List<RepairOrder>> list(@RequestParam(required = false) Long areaId,
                                                  @RequestParam(required = false) String status) {
        return ResponseEntity.ok(repairOrderService.search(areaId, status));
    }

    @GetMapping("/{id}")
    public ResponseEntity<RepairOrder> getById(@PathVariable Long id) {
        return ResponseEntity.ok(repairOrderService.findById(id));
    }

    @PostMapping("/{id}/accept")
    public ResponseEntity<RepairOrder> accept(@PathVariable Long id,
                                              @RequestBody RepairOrderHandleRequest request) {
        return ResponseEntity.ok(repairOrderService.accept(id, request.getRepairer()));
    }

    @PostMapping("/{id}/finish")
    public ResponseEntity<RepairOrder> finish(@PathVariable Long id,
                                              @RequestParam String result,
                                              @RequestBody(required = false) RepairOrderHandleRequest request) {
        RepairOrderHandleRequest body = request == null ? new RepairOrderHandleRequest() : request;
        return ResponseEntity.ok(repairOrderService.finish(id, result,
                body.getRepairNote(), body.getDeskStatusAfter(), body.getOperator()));
    }

    @PostMapping("/{id}/handle-desk")
    public ResponseEntity<RepairOrder> handleDesk(@PathVariable Long id,
                                                  @RequestBody RepairOrderHandleRequest request) {
        return ResponseEntity.ok(repairOrderService.handleDeskStatus(
                id, request.getDeskStatusAfter(), request.getOperator()));
    }
}
