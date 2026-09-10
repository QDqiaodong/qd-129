package com.example.service.impl;

import com.example.dto.BatchTransferRequest;
import com.example.entity.AreaChangeBatch;
import com.example.entity.AreaChangeBatchItem;
import com.example.entity.AreaChangeLog;
import com.example.entity.DeskChair;
import com.example.entity.ReadingArea;
import com.example.mapper.AreaChangeBatchItemMapper;
import com.example.mapper.AreaChangeBatchMapper;
import com.example.mapper.AreaChangeLogMapper;
import com.example.mapper.DeskChairMapper;
import com.example.mapper.ReadingAreaMapper;
import com.example.service.AreaChangeBatchService;
import com.example.vo.BatchTransferItemVO;
import com.example.vo.BatchTransferPreviewVO;
import com.example.vo.BatchTransferResultVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Service
public class AreaChangeBatchServiceImpl implements AreaChangeBatchService {

    private static final int MAX_BATCH_SIZE = 500;
    private static final DateTimeFormatter BATCH_NO_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    @Autowired
    private AreaChangeBatchMapper areaChangeBatchMapper;

    @Autowired
    private AreaChangeBatchItemMapper areaChangeBatchItemMapper;

    @Autowired
    private AreaChangeLogMapper areaChangeLogMapper;

    @Autowired
    private DeskChairMapper deskChairMapper;

    @Autowired
    private ReadingAreaMapper readingAreaMapper;

    private final TransactionTemplate transactionTemplate;

    public AreaChangeBatchServiceImpl(PlatformTransactionManager transactionManager) {
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Override
    public BatchTransferPreviewVO preview(BatchTransferRequest request) {
        ReadingArea targetArea = validateTargetArea(request.getTargetAreaId());
        List<Long> deskChairIds = normalizeIds(request.getDeskChairIds());

        List<BatchTransferItemVO> itemVOs = buildPreviewItems(deskChairIds, targetArea);
        int moveCount = (int) itemVOs.stream().filter(BatchTransferItemVO::getValid).count();
        int invalidCount = itemVOs.size() - moveCount;
        int alreadyInTargetCount = (int) itemVOs.stream()
                .filter(item -> Boolean.FALSE.equals(item.getValid())
                        && "ALREADY_IN_TARGET".equals(item.getStatus()))
                .count();

        BatchTransferPreviewVO vo = new BatchTransferPreviewVO();
        vo.setTargetAreaId(targetArea.getId());
        vo.setTargetAreaName(targetArea.getAreaName());
        vo.setSelectedCount(itemVOs.size());
        vo.setMoveCount(moveCount);
        vo.setInvalidCount(invalidCount);
        vo.setAlreadyInTargetCount(alreadyInTargetCount);
        vo.setCanSubmit(moveCount > 0 && invalidCount == 0);
        vo.setItems(itemVOs);
        return vo;
    }

    @Override
    public BatchTransferResultVO execute(BatchTransferRequest request) {
        String changeReason = requireText(request.getChangeReason(), "变更原因不能为空");
        String operator = requireText(request.getOperator(), "操作人不能为空");
        ReadingArea targetArea = validateTargetArea(request.getTargetAreaId());
        List<Long> deskChairIds = normalizeIds(request.getDeskChairIds());

        // 执行前快照校验，按去重后的勾选顺序保留每张桌椅的原归属
        Map<Long, DeskChair> snapshot = new LinkedHashMap<>();
        Map<Long, String> businessErrors = new LinkedHashMap<>();
        for (Long id : deskChairIds) {
            DeskChair deskChair = deskChairMapper.selectById(id);
            if (deskChair == null || deskChair.getStatus() == null || deskChair.getStatus() != 1) {
                businessErrors.put(id, "资产不存在或已停用");
                continue;
            }
            if (targetArea.getId().equals(deskChair.getAreaId())) {
                businessErrors.put(id, "ALREADY_IN_TARGET");
                continue;
            }
            snapshot.put(id, deskChair);
        }

        String batchNo = generateBatchNo();

        if (!businessErrors.isEmpty()) {
            // 存在不可迁移资产：整批不执行，但保留可追溯的失败批次
            AreaChangeBatch failedBatch = buildBatchHeader(batchNo, targetArea.getId(),
                    deskChairIds.size(), changeReason, operator);
            List<AreaChangeBatchItem> failedItems = buildItemsForValidationFailure(
                    failedBatch, deskChairIds, snapshot, businessErrors, targetArea.getId());
            persistFailedBatch(failedBatch, failedItems,
                    "存在 " + businessErrors.size() + " 项不可迁移资产，整批未执行");
            return buildResult(failedBatch.getId());
        }

        AreaChangeBatch batch = buildBatchHeader(batchNo, targetArea.getId(),
                snapshot.size(), changeReason, operator);
        List<AreaChangeBatchItem> items = buildItems(batch, snapshot, targetArea.getId());

        Long resultBatchId;
        try {
            transactionTemplate.executeWithoutResult(status -> {
                areaChangeBatchMapper.insert(batch);
                items.forEach(item -> {
                    item.setBatchId(batch.getId());
                    areaChangeBatchItemMapper.insert(item);
                });

                for (AreaChangeBatchItem item : items) {
                    DeskChair deskChair = snapshot.get(item.getDeskChairId());
                    deskChair.setAreaId(targetArea.getId());
                    int affected = deskChairMapper.updateById(deskChair);
                    if (affected != 1) {
                        throw new IllegalStateException("桌椅 " + item.getAssetCode() + " 更新失败");
                    }

                    AreaChangeLog changeLog = new AreaChangeLog();
                    changeLog.setDeskChairId(deskChair.getId());
                    changeLog.setOldAreaId(item.getOldAreaId());
                    changeLog.setNewAreaId(targetArea.getId());
                    changeLog.setChangeReason(changeReason);
                    changeLog.setOperator(operator);
                    changeLog.setBatchNo(batchNo);
                    String tableIndex = deskChair.getId() % 2 == 0 ? "00" : "01";
                    areaChangeLogMapper.insertLog(changeLog, tableIndex);

                    item.setChangeLogId(changeLog.getId());
                    item.setStatus(AreaChangeBatchItem.STATUS_SUCCESS);
                    areaChangeBatchItemMapper.updateById(item);
                }

                batch.setStatus(AreaChangeBatch.STATUS_SUCCESS);
                batch.setSuccessCount(items.size());
                batch.setFailCount(0);
                areaChangeBatchMapper.updateById(batch);
            });
            resultBatchId = batch.getId();
        } catch (Exception e) {
            // 事务已整体回滚：另起事务记录失败批次，保证可追溯
            AreaChangeBatch failedBatch = buildBatchHeader(batchNo, targetArea.getId(),
                    snapshot.size(), changeReason, operator);
            persistFailedBatch(failedBatch,
                    buildItemsForSystemFailure(batchNo, snapshot, targetArea.getId()),
                    truncate("执行异常，整批已回滚：" + e.getMessage()));
            resultBatchId = failedBatch.getId();
        }

        return buildResult(resultBatchId);
    }

    @Override
    public List<AreaChangeBatch> findAll() {
        return areaChangeBatchMapper.findAllWithArea();
    }

    @Override
    public AreaChangeBatch findById(Long id) {
        AreaChangeBatch batch = areaChangeBatchMapper.findByIdWithArea(id);
        if (batch == null) {
            throw new IllegalArgumentException("批次不存在");
        }
        batch.setItems(areaChangeBatchItemMapper.findByBatchIdWithArea(id));
        return batch;
    }

    private List<BatchTransferItemVO> buildPreviewItems(List<Long> deskChairIds, ReadingArea targetArea) {
        Map<Long, String> areaNameCache = new LinkedHashMap<>();
        areaNameCache.put(targetArea.getId(), targetArea.getAreaName());

        List<BatchTransferItemVO> result = new ArrayList<>();
        for (Long id : deskChairIds) {
            BatchTransferItemVO itemVO = new BatchTransferItemVO();
            itemVO.setDeskChairId(id);
            itemVO.setNewAreaId(targetArea.getId());
            itemVO.setNewAreaName(targetArea.getAreaName());

            DeskChair deskChair = deskChairMapper.selectById(id);
            if (deskChair == null || deskChair.getStatus() == null || deskChair.getStatus() != 1) {
                itemVO.setValid(false);
                itemVO.setStatus("INVALID");
                itemVO.setErrorMessage("资产不存在或已停用");
            } else if (targetArea.getId().equals(deskChair.getAreaId())) {
                fillDeskChairInfo(itemVO, deskChair, areaNameCache);
                itemVO.setNewAreaName(itemVO.getOldAreaName());
                itemVO.setValid(false);
                itemVO.setStatus("ALREADY_IN_TARGET");
                itemVO.setErrorMessage("已在目标分区，无需迁移");
            } else {
                fillDeskChairInfo(itemVO, deskChair, areaNameCache);
                itemVO.setValid(true);
                itemVO.setStatus("READY");
            }
            result.add(itemVO);
        }
        return result;
    }

    private void fillDeskChairInfo(BatchTransferItemVO itemVO, DeskChair deskChair,
                                   Map<Long, String> areaNameCache) {
        itemVO.setAssetCode(deskChair.getAssetCode());
        itemVO.setDimensions(deskChair.getDimensions());
        itemVO.setOldAreaId(deskChair.getAreaId());
        if (deskChair.getAreaId() != null) {
            itemVO.setOldAreaName(areaNameCache.computeIfAbsent(deskChair.getAreaId(),
                    key -> {
                        ReadingArea oldArea = readingAreaMapper.selectById(key);
                        return oldArea == null ? null : oldArea.getAreaName();
                    }));
        }
    }

    private ReadingArea validateTargetArea(Long targetAreaId) {
        if (targetAreaId == null) {
            throw new IllegalArgumentException("请选择目标分区");
        }
        ReadingArea area = readingAreaMapper.selectById(targetAreaId);
        if (area == null || area.getStatus() == null || area.getStatus() != 1) {
            throw new IllegalArgumentException("目标分区不存在或已停用");
        }
        return area;
    }

    private List<Long> normalizeIds(List<Long> rawIds) {
        if (rawIds == null || rawIds.isEmpty()) {
            throw new IllegalArgumentException("请至少勾选一张桌椅");
        }
        List<Long> ids = rawIds.stream().distinct().collect(Collectors.toList());
        if (ids.size() > MAX_BATCH_SIZE) {
            throw new IllegalArgumentException("单次最多迁移 " + MAX_BATCH_SIZE + " 张桌椅");
        }
        return ids;
    }

    private String requireText(String text, String message) {
        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return text.trim();
    }

    private AreaChangeBatch buildBatchHeader(String batchNo, Long targetAreaId, int totalCount,
                                             String changeReason, String operator) {
        AreaChangeBatch batch = new AreaChangeBatch();
        batch.setBatchNo(batchNo);
        batch.setTargetAreaId(targetAreaId);
        batch.setTotalCount(totalCount);
        batch.setSuccessCount(0);
        batch.setFailCount(totalCount);
        batch.setChangeReason(changeReason);
        batch.setOperator(operator);
        batch.setStatus(AreaChangeBatch.STATUS_PROCESSING);
        batch.setCreatedAt(LocalDateTime.now());
        return batch;
    }

    private List<AreaChangeBatchItem> buildItems(AreaChangeBatch batch,
                                                 Map<Long, DeskChair> snapshot, Long targetAreaId) {
        List<AreaChangeBatchItem> items = new ArrayList<>();
        for (DeskChair deskChair : snapshot.values()) {
            AreaChangeBatchItem item = new AreaChangeBatchItem();
            item.setBatchNo(batch.getBatchNo());
            item.setDeskChairId(deskChair.getId());
            item.setAssetCode(deskChair.getAssetCode());
            item.setOldAreaId(deskChair.getAreaId());
            item.setNewAreaId(targetAreaId);
            item.setStatus(AreaChangeBatchItem.STATUS_PENDING);
            items.add(item);
        }
        return items;
    }

    private List<AreaChangeBatchItem> buildItemsForValidationFailure(
            AreaChangeBatch batch, List<Long> deskChairIds, Map<Long, DeskChair> snapshot,
            Map<Long, String> businessErrors, Long targetAreaId) {
        List<AreaChangeBatchItem> items = new ArrayList<>();
        for (Long id : deskChairIds) {
            AreaChangeBatchItem item = new AreaChangeBatchItem();
            item.setBatchNo(batch.getBatchNo());
            item.setNewAreaId(targetAreaId);
            item.setStatus(AreaChangeBatchItem.STATUS_FAILED);
            DeskChair deskChair = snapshot.get(id);
            if (deskChair != null) {
                item.setDeskChairId(deskChair.getId());
                item.setAssetCode(deskChair.getAssetCode());
                item.setOldAreaId(deskChair.getAreaId());
            } else {
                item.setDeskChairId(id);
            }
            String error = businessErrors.get(id);
            item.setErrorMessage("ALREADY_IN_TARGET".equals(error)
                    ? "已在目标分区，无需迁移" : error);
            items.add(item);
        }
        return items;
    }

    private List<AreaChangeBatchItem> buildItemsForSystemFailure(
            String batchNo, Map<Long, DeskChair> snapshot, Long targetAreaId) {
        List<AreaChangeBatchItem> items = new ArrayList<>();
        for (DeskChair deskChair : snapshot.values()) {
            AreaChangeBatchItem item = new AreaChangeBatchItem();
            item.setBatchNo(batchNo);
            item.setDeskChairId(deskChair.getId());
            item.setAssetCode(deskChair.getAssetCode());
            item.setOldAreaId(deskChair.getAreaId());
            item.setNewAreaId(targetAreaId);
            item.setStatus(AreaChangeBatchItem.STATUS_FAILED);
            item.setErrorMessage("执行异常，整批已回滚");
            items.add(item);
        }
        return items;
    }

    private void persistFailedBatch(AreaChangeBatch batch, List<AreaChangeBatchItem> items, String errorMessage) {
        batch.setStatus(AreaChangeBatch.STATUS_FAILED);
        batch.setSuccessCount(0);
        batch.setFailCount(items.size());
        batch.setErrorMessage(truncate(errorMessage));
        transactionTemplate.executeWithoutResult(status -> {
            areaChangeBatchMapper.insert(batch);
            items.forEach(item -> {
                item.setBatchId(batch.getId());
                areaChangeBatchItemMapper.insert(item);
            });
            areaChangeBatchMapper.updateById(batch);
        });
    }

    private BatchTransferResultVO buildResult(Long batchId) {
        AreaChangeBatch batch = areaChangeBatchMapper.findByIdWithArea(batchId);
        List<AreaChangeBatchItem> items = areaChangeBatchItemMapper.findByBatchIdWithArea(batchId);
        batch.setItems(items);

        BatchTransferResultVO result = new BatchTransferResultVO();
        result.setBatchId(batch.getId());
        result.setBatchNo(batch.getBatchNo());
        result.setStatus(batch.getStatus());
        result.setTargetAreaId(batch.getTargetAreaId());
        result.setTargetAreaName(batch.getTargetAreaName());
        result.setTotalCount(batch.getTotalCount());
        result.setSuccessCount(batch.getSuccessCount());
        result.setFailCount(batch.getFailCount());
        result.setChangeReason(batch.getChangeReason());
        result.setOperator(batch.getOperator());
        result.setErrorMessage(batch.getErrorMessage());
        result.setCreatedAt(batch.getCreatedAt());
        result.setItems(items.stream().map(this::toItemVO).collect(Collectors.toList()));
        return result;
    }

    private BatchTransferItemVO toItemVO(AreaChangeBatchItem item) {
        BatchTransferItemVO vo = new BatchTransferItemVO();
        vo.setDeskChairId(item.getDeskChairId());
        vo.setAssetCode(item.getAssetCode());
        vo.setOldAreaId(item.getOldAreaId());
        vo.setOldAreaName(item.getOldAreaName());
        vo.setNewAreaId(item.getNewAreaId());
        vo.setNewAreaName(item.getNewAreaName());
        vo.setStatus(item.getStatus());
        vo.setErrorMessage(item.getErrorMessage());
        vo.setValid(AreaChangeBatchItem.STATUS_SUCCESS.equals(item.getStatus()));
        return vo;
    }

    private String generateBatchNo() {
        return "BAT" + LocalDateTime.now().format(BATCH_NO_FORMATTER)
                + String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
    }

    private String truncate(String text) {
        if (text == null) {
            return null;
        }
        return text.length() > 1000 ? text.substring(0, 1000) : text;
    }
}
