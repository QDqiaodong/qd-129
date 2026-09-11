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

    /** 不可迁移原因码 */
    private static final String REASON_NOT_FOUND = "NOT_FOUND";
    private static final String REASON_DISABLED = "DISABLED";
    private static final String REASON_ALREADY_IN_TARGET = "ALREADY_IN_TARGET";

    private static final String MESSAGE_NOT_FOUND = "资产不存在或已删除";
    private static final String MESSAGE_DISABLED = "资产已停用，无法调区";
    private static final String MESSAGE_ALREADY_IN_TARGET = "已在目标分区，无需迁移";
    private static final String MESSAGE_SKIPPED_BY_BATCH = "整批含不可迁移资产，未执行迁移";

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
        ValidationSnapshot snapshot = validateSelection(normalizeIds(request.getDeskChairIds()), targetArea);

        List<BatchTransferItemVO> itemVOs = buildPreviewItems(snapshot, targetArea);
        int moveCount = snapshot.validIds.size();
        int invalidCount = snapshot.invalidReasons.size();
        int alreadyInTargetCount = countReasons(snapshot, REASON_ALREADY_IN_TARGET);

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
        // 预览/执行共用同一份去重与失效校验，保证提交数量与界面提示一致
        ValidationSnapshot snapshot = validateSelection(normalizeIds(request.getDeskChairIds()), targetArea);

        String batchNo = generateBatchNo();

        if (!snapshot.invalidReasons.isEmpty()) {
            // 存在不可迁移资产：整批不执行，但保留可追溯的失败批次，并逐项给出原因
            AreaChangeBatch failedBatch = buildBatchHeader(batchNo, targetArea.getId(),
                    snapshot.ids.size(), changeReason, operator);
            List<AreaChangeBatchItem> failedItems = buildItemsForValidationFailure(
                    failedBatch, snapshot, targetArea.getId());
            persistFailedBatch(failedBatch, failedItems,
                    "存在 " + snapshot.invalidReasons.size() + " 项不可迁移资产，整批未执行");
            return buildResult(failedBatch.getId());
        }

        AreaChangeBatch batch = buildBatchHeader(batchNo, targetArea.getId(),
                snapshot.validIds.size(), changeReason, operator);
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
                    DeskChair deskChair = snapshot.foundDeskChairs.get(item.getDeskChairId());
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
                    snapshot.validIds.size(), changeReason, operator);
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

    /**
     * 按去重后的勾选顺序做统一快照校验：区分有效资产、已删除资产、已停用资产和已在目标分区的资产。
     */
    private ValidationSnapshot validateSelection(List<Long> deskChairIds, ReadingArea targetArea) {
        ValidationSnapshot snapshot = new ValidationSnapshot();
        snapshot.ids = deskChairIds;
        for (Long id : deskChairIds) {
            DeskChair deskChair = deskChairMapper.selectById(id);
            if (deskChair == null) {
                snapshot.invalidReasons.put(id, REASON_NOT_FOUND);
                continue;
            }
            snapshot.foundDeskChairs.put(id, deskChair);
            if (deskChair.getStatus() == null || deskChair.getStatus() != 1) {
                snapshot.invalidReasons.put(id, REASON_DISABLED);
            } else if (targetArea.getId().equals(deskChair.getAreaId())) {
                snapshot.invalidReasons.put(id, REASON_ALREADY_IN_TARGET);
            } else {
                snapshot.validIds.add(id);
            }
        }
        return snapshot;
    }

    private int countReasons(ValidationSnapshot snapshot, String reasonCode) {
        return (int) snapshot.invalidReasons.values().stream().filter(reasonCode::equals).count();
    }

    private List<BatchTransferItemVO> buildPreviewItems(ValidationSnapshot snapshot, ReadingArea targetArea) {
        Map<Long, String> areaNameCache = new LinkedHashMap<>();
        areaNameCache.put(targetArea.getId(), targetArea.getAreaName());

        List<BatchTransferItemVO> result = new ArrayList<>();
        for (Long id : snapshot.ids) {
            BatchTransferItemVO itemVO = new BatchTransferItemVO();
            itemVO.setDeskChairId(id);
            itemVO.setNewAreaId(targetArea.getId());
            itemVO.setNewAreaName(targetArea.getAreaName());

            String reasonCode = snapshot.invalidReasons.get(id);
            DeskChair deskChair = snapshot.foundDeskChairs.get(id);
            if (reasonCode == null) {
                // 当前有效资产：预览与执行只处理这些项
                fillDeskChairInfo(itemVO, deskChair, areaNameCache);
                itemVO.setValid(true);
                itemVO.setStatus("READY");
            } else if (REASON_NOT_FOUND.equals(reasonCode)) {
                itemVO.setValid(false);
                itemVO.setStatus("INVALID");
                itemVO.setReasonCode(REASON_NOT_FOUND);
                itemVO.setErrorMessage(MESSAGE_NOT_FOUND);
            } else {
                // 已停用或已在目标分区的资产仍展示其当前归属，便于逐项核对
                fillDeskChairInfo(itemVO, deskChair, areaNameCache);
                itemVO.setValid(false);
                itemVO.setStatus("INVALID");
                itemVO.setReasonCode(reasonCode);
                if (REASON_DISABLED.equals(reasonCode)) {
                    itemVO.setErrorMessage(MESSAGE_DISABLED);
                } else {
                    itemVO.setNewAreaName(itemVO.getOldAreaName());
                    itemVO.setErrorMessage(MESSAGE_ALREADY_IN_TARGET);
                }
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
        // 跨筛选勾选可能产生重复 ID，统一去重，保证批量数量与唯一资产一致
        List<Long> ids = rawIds.stream().filter(java.util.Objects::nonNull)
                .distinct().collect(Collectors.toList());
        if (ids.isEmpty()) {
            throw new IllegalArgumentException("请至少勾选一张桌椅");
        }
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
                                                 ValidationSnapshot snapshot, Long targetAreaId) {
        List<AreaChangeBatchItem> items = new ArrayList<>();
        for (Long id : snapshot.validIds) {
            DeskChair deskChair = snapshot.foundDeskChairs.get(id);
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
            AreaChangeBatch batch, ValidationSnapshot snapshot, Long targetAreaId) {
        List<AreaChangeBatchItem> items = new ArrayList<>();
        for (Long id : snapshot.ids) {
            AreaChangeBatchItem item = new AreaChangeBatchItem();
            item.setBatchNo(batch.getBatchNo());
            item.setDeskChairId(id);
            item.setNewAreaId(targetAreaId);
            // 整批未执行，没有一项成功；逐项原因区分真正失效的资产与随批跳过的资产
            item.setStatus(AreaChangeBatchItem.STATUS_FAILED);
            DeskChair deskChair = snapshot.foundDeskChairs.get(id);
            if (deskChair != null) {
                item.setAssetCode(deskChair.getAssetCode());
                item.setOldAreaId(deskChair.getAreaId());
            }
            item.setErrorMessage(snapshot.invalidReasons.containsKey(id)
                    ? reasonMessage(snapshot.invalidReasons.get(id))
                    : MESSAGE_SKIPPED_BY_BATCH);
            items.add(item);
        }
        return items;
    }

    private List<AreaChangeBatchItem> buildItemsForSystemFailure(
            String batchNo, ValidationSnapshot snapshot, Long targetAreaId) {
        List<AreaChangeBatchItem> items = new ArrayList<>();
        for (Long id : snapshot.validIds) {
            DeskChair deskChair = snapshot.foundDeskChairs.get(id);
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

    private String reasonMessage(String reasonCode) {
        if (REASON_NOT_FOUND.equals(reasonCode)) {
            return MESSAGE_NOT_FOUND;
        }
        if (REASON_DISABLED.equals(reasonCode)) {
            return MESSAGE_DISABLED;
        }
        if (REASON_ALREADY_IN_TARGET.equals(reasonCode)) {
            return MESSAGE_ALREADY_IN_TARGET;
        }
        return "资产不可迁移";
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

    /**
     * 一次批量请求的校验快照：ids 为去重后的全部勾选，validIds 为当前仍有效的资产，
     * foundDeskChairs 缓存仍能查到的资产，invalidReasons 按资产逐项记录不可迁移原因码。
     */
    private static class ValidationSnapshot {
        private List<Long> ids = new ArrayList<>();
        private final List<Long> validIds = new ArrayList<>();
        private final Map<Long, DeskChair> foundDeskChairs = new LinkedHashMap<>();
        private final Map<Long, String> invalidReasons = new LinkedHashMap<>();
    }
}
