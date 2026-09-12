package com.example.controller;

import com.example.dto.LostItemClaimRequest;
import com.example.dto.LostItemCreateRequest;
import com.example.entity.LostItem;
import com.example.service.LostItemService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/lost-item")
@CrossOrigin(origins = "*")
public class LostItemController {

    @Autowired
    private LostItemService lostItemService;

    @PostMapping
    public ResponseEntity<LostItem> create(@RequestBody LostItemCreateRequest request) {
        return ResponseEntity.ok(lostItemService.create(request));
    }

    @GetMapping
    public ResponseEntity<List<LostItem>> list(@RequestParam(required = false) Long areaId,
                                               @RequestParam(required = false) String status) {
        return ResponseEntity.ok(lostItemService.search(areaId, status));
    }

    @GetMapping("/pending")
    public ResponseEntity<List<LostItem>> listPending(@RequestParam Long areaId) {
        return ResponseEntity.ok(lostItemService.findPendingByArea(areaId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<LostItem> getById(@PathVariable Long id) {
        return ResponseEntity.ok(lostItemService.findById(id));
    }

    @PostMapping("/{id}/claim")
    public ResponseEntity<LostItem> claim(@PathVariable Long id,
                                          @RequestBody LostItemClaimRequest request) {
        return ResponseEntity.ok(lostItemService.claim(id, request));
    }
}
