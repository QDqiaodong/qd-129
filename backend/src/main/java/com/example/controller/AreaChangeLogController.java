package com.example.controller;

import com.example.entity.AreaChangeLog;
import com.example.service.AreaChangeLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/area-change-log")
@CrossOrigin(origins = "*")
public class AreaChangeLogController {

    @Autowired
    private AreaChangeLogService areaChangeLogService;

    @GetMapping
    public ResponseEntity<List<AreaChangeLog>> getAll() {
        return ResponseEntity.ok(areaChangeLogService.findAll());
    }

    @GetMapping("/desk-chair/{deskChairId}")
    public ResponseEntity<List<AreaChangeLog>> getByDeskChairId(@PathVariable Long deskChairId) {
        return ResponseEntity.ok(areaChangeLogService.findByDeskChairId(deskChairId));
    }
}