package com.example.controller;

import com.example.entity.Tag;
import com.example.service.TagService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tag")
@CrossOrigin(origins = "*")
public class TagController {

    @Autowired
    private TagService tagService;

    @GetMapping
    public ResponseEntity<List<Tag>> getAll() {
        return ResponseEntity.ok(tagService.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Tag> getById(@PathVariable Long id) {
        return ResponseEntity.ok(tagService.findById(id));
    }

    @GetMapping("/code/{code}")
    public ResponseEntity<Tag> getByCode(@PathVariable String code) {
        return ResponseEntity.ok(tagService.findByCode(code));
    }

    @PostMapping
    public ResponseEntity<Tag> create(@RequestBody Tag tag) {
        return ResponseEntity.ok(tagService.save(tag));
    }

    @PutMapping
    public ResponseEntity<Tag> update(@RequestBody Tag tag) {
        return ResponseEntity.ok(tagService.update(tag));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        tagService.deleteById(id);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/desk-chair/{deskChairId}/tag/{tagId}")
    public ResponseEntity<Void> deleteTagFromDeskChair(@PathVariable Long deskChairId, @PathVariable Long tagId) {
        tagService.deleteTagFromDeskChair(deskChairId, tagId);
        return ResponseEntity.ok().build();
    }
}