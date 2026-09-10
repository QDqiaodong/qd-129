package com.example.service;

import com.example.dto.BatchTransferRequest;
import com.example.entity.AreaChangeBatch;
import com.example.vo.BatchTransferPreviewVO;
import com.example.vo.BatchTransferResultVO;

import java.util.List;

public interface AreaChangeBatchService {

    BatchTransferPreviewVO preview(BatchTransferRequest request);

    BatchTransferResultVO execute(BatchTransferRequest request);

    List<AreaChangeBatch> findAll();

    AreaChangeBatch findById(Long id);
}
