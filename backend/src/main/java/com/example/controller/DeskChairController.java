package com.example.controller;

import com.example.entity.DeskChair;
import com.example.service.DeskChairService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/desk-chair")
@CrossOrigin(origins = "*")
public class DeskChairController {

    @Autowired
    private DeskChairService deskChairService;

    @GetMapping
    public ResponseEntity<List<DeskChair>> getAll() {
        return ResponseEntity.ok(deskChairService.findAll());
    }

    @GetMapping("/area/{areaId}")
    public ResponseEntity<List<DeskChair>> getByAreaId(@PathVariable Long areaId) {
        return ResponseEntity.ok(deskChairService.findByAreaId(areaId));
    }

    @GetMapping("/tag/{tagId}")
    public ResponseEntity<List<DeskChair>> getByTagId(@PathVariable Long tagId) {
        return ResponseEntity.ok(deskChairService.findByTagId(tagId));
    }

    @GetMapping("/tags")
    public ResponseEntity<List<DeskChair>> getByTagIds(@RequestParam List<Long> tagIds) {
        return ResponseEntity.ok(deskChairService.findByTagIds(tagIds));
    }

    @GetMapping("/search")
    public ResponseEntity<List<DeskChair>> search(@RequestParam(required = false) Long areaId,
                                                  @RequestParam(required = false) List<Long> tagIds) {
        return ResponseEntity.ok(deskChairService.search(areaId, tagIds));
    }

    @GetMapping("/{id}")
    public ResponseEntity<DeskChair> getById(@PathVariable Long id) {
        return ResponseEntity.ok(deskChairService.findById(id));
    }

    @GetMapping("/code/{assetCode}")
    public ResponseEntity<DeskChair> getByAssetCode(@PathVariable String assetCode) {
        return ResponseEntity.ok(deskChairService.findByAssetCode(assetCode));
    }

    @PostMapping
    public ResponseEntity<DeskChair> create(@RequestBody DeskChair deskChair) {
        return ResponseEntity.ok(deskChairService.save(deskChair));
    }

    @PutMapping
    public ResponseEntity<DeskChair> update(@RequestBody DeskChair deskChair) {
        return ResponseEntity.ok(deskChairService.update(deskChair));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        deskChairService.deleteById(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/tags")
    public ResponseEntity<Void> bindTags(@PathVariable Long id, @RequestBody List<Long> tagIds) {
        deskChairService.bindTags(id, tagIds);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/update-area")
    public ResponseEntity<Void> updateArea(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Long newAreaId = Long.parseLong(body.get("newAreaId").toString());
        String changeReason = body.get("changeReason").toString();
        String operator = body.getOrDefault("operator", "admin").toString();
        deskChairService.updateArea(id, newAreaId, changeReason, operator);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/dimensions")
    public ResponseEntity<List<DeskChair>> getStandardDimensions() {
        return ResponseEntity.ok(deskChairService.getStandardDimensions());
    }
}