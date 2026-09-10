package com.example.controller;

import com.example.dto.BatchTransferRequest;
import com.example.entity.AreaChangeBatch;
import com.example.service.AreaChangeBatchService;
import com.example.vo.BatchTransferPreviewVO;
import com.example.vo.BatchTransferResultVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/area-change-batch")
@CrossOrigin(origins = "*")
public class AreaChangeBatchController {

    @Autowired
    private AreaChangeBatchService areaChangeBatchService;

    @PostMapping("/preview")
    public ResponseEntity<BatchTransferPreviewVO> preview(@RequestBody BatchTransferRequest request) {
        return ResponseEntity.ok(areaChangeBatchService.preview(request));
    }

    @PostMapping("/execute")
    public ResponseEntity<BatchTransferResultVO> execute(@RequestBody BatchTransferRequest request) {
        return ResponseEntity.ok(areaChangeBatchService.execute(request));
    }

    @GetMapping
    public ResponseEntity<List<AreaChangeBatch>> getAll() {
        return ResponseEntity.ok(areaChangeBatchService.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<AreaChangeBatch> getById(@PathVariable Long id) {
        return ResponseEntity.ok(areaChangeBatchService.findById(id));
    }
}
