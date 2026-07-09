package com.example.controller;

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

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        readingAreaService.deleteById(id);
        return ResponseEntity.ok().build();
    }
}