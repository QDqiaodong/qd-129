package com.example.controller;

import com.example.dto.StocktakeCreateRequest;
import com.example.dto.StocktakeHandleRequest;
import com.example.dto.StocktakeSubmitRequest;
import com.example.entity.StocktakeBatch;
import com.example.service.StocktakeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/stocktake")
@CrossOrigin(origins = "*")
public class StocktakeController {

    @Autowired
    private StocktakeService stocktakeService;

    @PostMapping("/batch")
    public ResponseEntity<StocktakeBatch> createBatch(@RequestBody StocktakeCreateRequest request) {
        return ResponseEntity.ok(stocktakeService.createBatch(request));
    }

    @GetMapping("/batch")
    public ResponseEntity<List<StocktakeBatch>> listBatches() {
        return ResponseEntity.ok(stocktakeService.findAll());
    }

    @GetMapping("/batch/{id}")
    public ResponseEntity<StocktakeBatch> getBatch(@PathVariable Long id) {
        return ResponseEntity.ok(stocktakeService.findById(id));
    }

    @PostMapping("/batch/{id}/submit")
    public ResponseEntity<StocktakeBatch> submitActuals(@PathVariable Long id,
                                                        @RequestBody StocktakeSubmitRequest request) {
        return ResponseEntity.ok(stocktakeService.submitActuals(
                id, request.getLines(), request.getOperator(), request.getRemark()));
    }

    @PostMapping("/batch/{batchId}/item/{itemId}/confirm")
    public ResponseEntity<StocktakeBatch> confirmItem(@PathVariable Long batchId,
                                                      @PathVariable Long itemId,
                                                      @RequestBody StocktakeHandleRequest request) {
        return ResponseEntity.ok(stocktakeService.confirmItem(batchId, itemId, request));
    }

    @PostMapping("/batch/{batchId}/item/{itemId}/recheck")
    public ResponseEntity<StocktakeBatch> recheckItem(@PathVariable Long batchId,
                                                      @PathVariable Long itemId,
                                                      @RequestBody StocktakeHandleRequest request) {
        return ResponseEntity.ok(stocktakeService.recheckItem(batchId, itemId, request));
    }

    @PostMapping("/batch/{id}/complete")
    public ResponseEntity<StocktakeBatch> completeBatch(@PathVariable Long id,
                                                        @RequestBody StocktakeHandleRequest request) {
        return ResponseEntity.ok(stocktakeService.completeBatch(id, request));
    }
}
