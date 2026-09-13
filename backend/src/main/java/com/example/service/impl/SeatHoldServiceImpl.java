package com.example.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.dto.SeatHoldCreateRequest;
import com.example.dto.SeatHoldHandleRequest;
import com.example.dto.SeatHoldHoldRequest;
import com.example.entity.DeskChair;
import com.example.entity.LostItem;
import com.example.entity.ReadingArea;
import com.example.entity.RepairOrder;
import com.example.entity.SeatHoldBatch;
import com.example.entity.SeatHoldItem;
import com.example.mapper.DeskChairMapper;
import com.example.mapper.LostItemMapper;
import com.example.mapper.ReadingAreaMapper;
import com.example.mapper.RepairOrderMapper;
import com.example.mapper.SeatHoldBatchMapper;
import com.example.mapper.SeatHoldItemMapper;
import com.example.service.SeatHoldService;
import com.example.vo.SeatHoldClearingItemVO;
import com.example.vo.SeatHoldClearingVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Service
public class SeatHoldServiceImpl implements SeatHoldService {

    private static final int MAX_ASSETS = 500;
    private static final DateTimeFormatter BATCH_NO_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final DateTimeFormatter CLOSED_UNTIL_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @Autowired
    private SeatHoldBatchMapper batchMapper;

    @Autowired
    private SeatHoldItemMapper itemMapper;

    @Autowired
    private DeskChairMapper deskChairMapper;

    @Autowired
    private ReadingAreaMapper readingAreaMapper;

    @Autowired
    private RepairOrderMapper repairOrderMapper;

    @Autowired
    private LostItemMapper lostItemMapper;

    private final TransactionTemplate transactionTemplate;

    public SeatHoldServiceImpl(PlatformTransactionManager transactionManager) {
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Override
    public SeatHoldBatch createBatch(SeatHoldCreateRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("占座批次内容不能为空");
        }
        Long areaId = request.getAreaId();
        if (areaId == null) {
            throw new IllegalArgumentException("请选择占座分区");
        }
        String timeSlot = requireText(request.getTimeSlot(), "请选择高峰时段");
        String operator = requireText(request.getOperator(), "操作人不能为空");
        ReadingArea area = readingAreaMapper.selectById(areaId);
        if (area == null || area.getStatus() == null || area.getStatus() != 1) {
            throw new IllegalArgumentException("占座分区不存在或已停用");
        }
        // 挂着“今日闭馆”牌且未到结束时刻的分区不能开新批，错误消息必须带出结束时刻，
        // 避免只说不能开却不知道何时解禁；到期后 closed_until 留在库里但不再产生闭馆效力
        if (area.getClosedUntil() != null && area.getClosedUntil().isAfter(LocalDateTime.now())) {
            throw new IllegalArgumentException("分区「" + area.getAreaName()
                    + "」今日闭馆，闭馆至 " + area.getClosedUntil().format(CLOSED_UNTIL_FORMATTER)
                    + "，结束时刻后再开高峰占座");
        }
        List<Long> deskChairIds = normalizeIds(request.getDeskChairIds());
        if (deskChairIds.isEmpty()) {
            throw new IllegalArgumentException("请从桌椅档案勾选至少一件占住资产");
        }

        return transactionTemplate.execute(status -> {
            SeatHoldBatch batch = new SeatHoldBatch();
            batch.setBatchNo(generateBatchNo());
            batch.setAreaId(area.getId());
            batch.setTimeSlot(truncate(timeSlot, 100));
            batch.setRemark(truncate(request.getRemark(), 500));
            batch.setOperator(operator);
            batch.setStatus(SeatHoldBatch.STATUS_OPEN);
            batch.setCreatedAt(LocalDateTime.now());
            batchMapper.insert(batch);
            holdAssets(batch, deskChairIds, operator);
            refreshCounts(batch);
            return findById(batch.getId());
        });
    }

    @Override
    public List<SeatHoldBatch> search(Long areaId, String status) {
        if (status != null) {
            status = status.trim();
            if (status.isEmpty()) {
                status = null;
            } else if (!SeatHoldBatch.STATUS_OPEN.equals(status)
                    && !SeatHoldBatch.STATUS_ENDED.equals(status)) {
                throw new IllegalArgumentException("批次状态不合法");
            }
        }
        return batchMapper.search(areaId, status);
    }

    @Override
    public SeatHoldBatch findById(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("缺少占座批次");
        }
        SeatHoldBatch batch = batchMapper.findByIdWithArea(id);
        if (batch == null) {
            throw new IllegalArgumentException("占座批次不存在");
        }
        batch.setItems(itemMapper.findByBatchId(id));
        return batch;
    }

    @Override
    public List<SeatHoldItem> findOpenHolds(Long areaId) {
        return itemMapper.findOpenHolds(areaId);
    }

    @Override
    public SeatHoldBatch hold(Long batchId, SeatHoldHoldRequest request) {
        SeatHoldBatch batch = requireBatch(batchId);
        requireOpen(batch);
        String operator = requireText(request == null ? null : request.getOperator(), "操作人不能为空");
        List<Long> deskChairIds = normalizeIds(request == null ? null : request.getDeskChairIds());
        if (deskChairIds.isEmpty()) {
            throw new IllegalArgumentException("请勾选需要追加占住的资产");
        }

        transactionTemplate.executeWithoutResult(status -> {
            holdAssets(batch, deskChairIds, operator);
            batch.setUpdatedAt(LocalDateTime.now());
            refreshCounts(batch);
        });
        return findById(batchId);
    }

    @Override
    public SeatHoldBatch release(Long batchId, Long itemId, SeatHoldHandleRequest request) {
        SeatHoldBatch batch = requireBatch(batchId);
        requireOpen(batch);
        SeatHoldItem item = requireItem(batch, itemId);
        if (!SeatHoldItem.STATUS_HOLDING.equals(item.getItemStatus())) {
            throw new IllegalArgumentException("仅在占资产可以当场释放，当前状态：" + itemStatusText(item.getItemStatus()));
        }
        String operator = requireText(request == null ? null : request.getOperator(), "操作人不能为空");
        doRelease(batch, item, operator);
        return findById(batchId);
    }

    @Override
    public SeatHoldBatch markTimeout(Long batchId, Long itemId, SeatHoldHandleRequest request) {
        SeatHoldBatch batch = requireBatch(batchId);
        requireOpen(batch);
        SeatHoldItem item = requireItem(batch, itemId);
        if (!SeatHoldItem.STATUS_HOLDING.equals(item.getItemStatus())) {
            throw new IllegalArgumentException("仅在占资产可以标记超时未到，当前状态：" + itemStatusText(item.getItemStatus()));
        }
        String operator = requireText(request == null ? null : request.getOperator(), "操作人不能为空");

        transactionTemplate.executeWithoutResult(status -> {
            LocalDateTime now = LocalDateTime.now();
            itemMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<SeatHoldItem>()
                    .eq(SeatHoldItem::getId, item.getId())
                    .set(SeatHoldItem::getItemStatus, SeatHoldItem.STATUS_TIMEOUT)
                    .set(SeatHoldItem::getTimeoutBy, operator)
                    .set(SeatHoldItem::getTimeoutAt, now)
                    .set(SeatHoldItem::getUpdatedAt, now));
            item.setItemStatus(SeatHoldItem.STATUS_TIMEOUT);
            item.setTimeoutBy(operator);
            item.setTimeoutAt(now);
            // 超时未到期间桌椅继续停用，等待整批结束清场处置
            refreshCounts(batch);
        });
        return findById(batchId);
    }

    @Override
    public SeatHoldBatch revertTimeout(Long batchId, Long itemId, SeatHoldHandleRequest request) {
        SeatHoldBatch batch = requireBatch(batchId);
        requireOpen(batch);
        SeatHoldItem item = requireItem(batch, itemId);
        if (!SeatHoldItem.STATUS_TIMEOUT.equals(item.getItemStatus())) {
            throw new IllegalArgumentException("仅超时未到的明细可以撤回标记，当前状态：" + itemStatusText(item.getItemStatus()));
        }
        requireText(request == null ? null : request.getOperator(), "操作人不能为空");

        transactionTemplate.executeWithoutResult(status -> {
            item.setItemStatus(SeatHoldItem.STATUS_HOLDING);
            item.setTimeoutBy(null);
            item.setTimeoutAt(null);
            item.setUpdatedAt(LocalDateTime.now());
            // 清空超时操作人/时间须显式 set，NOT_NULL 更新策略会跳过 null
            itemMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<SeatHoldItem>()
                    .eq(SeatHoldItem::getId, item.getId())
                    .set(SeatHoldItem::getItemStatus, SeatHoldItem.STATUS_HOLDING)
                    .set(SeatHoldItem::getTimeoutBy, null)
                    .set(SeatHoldItem::getTimeoutAt, null)
                    .set(SeatHoldItem::getUpdatedAt, LocalDateTime.now()));
            refreshCounts(batch);
        });
        return findById(batchId);
    }

    @Override
    public SeatHoldBatch finishBatch(Long batchId, SeatHoldHandleRequest request) {
        SeatHoldBatch batch = requireBatch(batchId);
        requireOpen(batch);
        requireText(request == null ? null : request.getOperator(), "操作人不能为空");

        List<SeatHoldItem> items = itemMapper.findByBatchId(batchId);
        if (items.isEmpty()) {
            throw new IllegalArgumentException("该批次没有占住明细，无需结束");
        }

        transactionTemplate.executeWithoutResult(status -> {
            // 整批结束：仍占着的资产（在占/超时未到）保持停用，已释放的已在释放时恢复可用；
            // 桌椅状态不做批量改写，批次/明细状态与桌椅实时状态始终可以对照一致
            batch.setStatus(SeatHoldBatch.STATUS_ENDED);
            batch.setEndedAt(LocalDateTime.now());
            batch.setUpdatedAt(LocalDateTime.now());
            batchMapper.updateById(batch);
        });
        return findById(batchId);
    }

    @Override
    public SeatHoldBatch releaseLegacy(Long batchId, Long itemId, SeatHoldHandleRequest request) {
        SeatHoldBatch batch = requireBatch(batchId);
        if (!SeatHoldBatch.STATUS_ENDED.equals(batch.getStatus())) {
            throw new IllegalArgumentException("仅已结束批次的遗留停用资产走清场释放，请先在进行中批次当场释放");
        }
        SeatHoldItem item = requireItem(batch, itemId);
        if (SeatHoldItem.STATUS_RELEASED.equals(item.getItemStatus())) {
            throw new IllegalArgumentException("该资产已释放，无需重复处置");
        }
        String operator = requireText(request == null ? null : request.getOperator(), "操作人不能为空");
        doRelease(batch, item, operator);
        return findById(batchId);
    }

    @Override
    public SeatHoldClearingVO getClearing(Long batchId) {
        SeatHoldBatch batch = requireBatch(batchId);
        if (!SeatHoldBatch.STATUS_ENDED.equals(batch.getStatus())) {
            throw new IllegalArgumentException("占座批次 " + batch.getBatchNo() + " 仍在进行中，整批结束后才生成清场清单");
        }
        List<SeatHoldItem> items = itemMapper.findByBatchId(batchId);

        // 一次性取出本批涉及资产的未闭环报修单，避免逐件查询；口径与释放时的报修拦截一致
        List<Long> deskChairIds = items.stream()
                .map(SeatHoldItem::getDeskChairId)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, List<RepairOrder>> openRepairMap = deskChairIds.isEmpty()
                ? Map.of()
                : repairOrderMapper.findOpenByDeskChairIds(deskChairIds).stream()
                        .collect(Collectors.groupingBy(RepairOrder::getDeskChairId,
                                LinkedHashMap::new, Collectors.toList()));

        SeatHoldClearingVO clearing = new SeatHoldClearingVO();
        clearing.setBatchId(batch.getId());
        clearing.setBatchNo(batch.getBatchNo());
        clearing.setBatchStatus(batch.getStatus());
        clearing.setRestorableItems(new ArrayList<>());
        clearing.setTimeoutDisabledItems(new ArrayList<>());
        clearing.setRepairDisabledItems(new ArrayList<>());

        for (SeatHoldItem item : items) {
            // 清场清单只收录仍停用的桌椅；已恢复可用的不在清场范围
            if (!Integer.valueOf(0).equals(item.getDeskStatus())) {
                continue;
            }
            List<RepairOrder> openRepairs = openRepairMap.getOrDefault(item.getDeskChairId(), List.of());
            SeatHoldClearingItemVO row = toClearingItem(item, openRepairs);
            // 报修锁定优先：有未闭环报修单的桌椅无论明细状态如何都归入报修停用，
            // 释放只闭环占座明细，桌椅保持停用，不能恢复成可用
            if (!openRepairs.isEmpty()) {
                clearing.getRepairDisabledItems().add(row);
            } else if (SeatHoldItem.STATUS_TIMEOUT.equals(item.getItemStatus())) {
                clearing.getTimeoutDisabledItems().add(row);
            } else if (SeatHoldItem.STATUS_HOLDING.equals(item.getItemStatus())) {
                clearing.getRestorableItems().add(row);
            }
            // 已释放但仍停用且无未闭环报修：属释放后的其他停用原因，与本次占座无关，不列入清单
        }
        clearing.setStillDisabledCount(clearing.getRestorableItems().size()
                + clearing.getTimeoutDisabledItems().size()
                + clearing.getRepairDisabledItems().size());
        return clearing;
    }

    private SeatHoldClearingItemVO toClearingItem(SeatHoldItem item, List<RepairOrder> openRepairs) {
        SeatHoldClearingItemVO row = new SeatHoldClearingItemVO();
        row.setItemId(item.getId());
        row.setDeskChairId(item.getDeskChairId());
        row.setAssetCode(item.getAssetCode());
        row.setItemStatus(item.getItemStatus());
        row.setPreviousDeskStatus(item.getPreviousDeskStatus());
        row.setDeskStatus(item.getDeskStatus());
        row.setOpenRepairCount(openRepairs.size());
        row.setOpenRepairOrderNos(openRepairs.isEmpty()
                ? null
                : openRepairs.stream().map(RepairOrder::getOrderNo).collect(Collectors.joining("、")));
        row.setReleasable(!SeatHoldItem.STATUS_RELEASED.equals(item.getItemStatus()));
        row.setReleasedBy(item.getReleasedBy());
        row.setReleasedAt(item.getReleasedAt());
        row.setTimeoutBy(item.getTimeoutBy());
        row.setTimeoutAt(item.getTimeoutAt());
        return row;
    }

    // ==================== 内部方法 ====================

    /**
     * 勾选资产占住：逐个校验归属分区、实时状态和跨批次占用，
     * 同一件资产被其他进行中批次（含超时未到）占着时明确阻止并给出批次号。
     */
    private void holdAssets(SeatHoldBatch batch, List<Long> deskChairIds, String operator) {
        LocalDateTime now = LocalDateTime.now();
        List<SeatHoldItem> newItems = new ArrayList<>();
        Map<Long, DeskChair> toDisable = new LinkedHashMap<>();

        for (Long deskChairId : deskChairIds) {
            DeskChair deskChair = deskChairMapper.selectById(deskChairId);
            if (deskChair == null) {
                throw new IllegalArgumentException("勾选的桌椅资产不存在（ID：" + deskChairId + "）");
            }
            if (!Objects.equals(deskChair.getAreaId(), batch.getAreaId())) {
                throw new IllegalArgumentException("资产 " + deskChair.getAssetCode()
                        + " 不属于占座分区，请重新勾选");
            }
            // 仍待领取遗失物品的桌椅不能开高峰占座：给出遗失单号和物品名称，领取闭环后才放行
            LostItem pendingLost = lostItemMapper.findPendingByDeskChair(deskChairId);
            if (pendingLost != null) {
                throw new IllegalArgumentException("资产 " + deskChair.getAssetCode()
                        + " 旁有待领取遗失物品（" + pendingLost.getItemNo() + " "
                        + pendingLost.getItemName() + "），领取闭环前不能开高峰占座");
            }
            if (Integer.valueOf(0).equals(deskChair.getStatus())) {
                // 停用原因可能是报修或其他占座批次，优先给出在占批次的明确提示
                SeatHoldItem active = findActiveHold(deskChairId);
                if (active != null) {
                    throw new IllegalArgumentException("资产 " + deskChair.getAssetCode()
                            + " 已被进行中批次 " + active.getBatchNo() + " 占住（"
                            + (SeatHoldItem.STATUS_TIMEOUT.equals(active.getItemStatus()) ? "超时未到" : "在占")
                            + "），请先释放后再开批");
                }
                throw new IllegalArgumentException("资产 " + deskChair.getAssetCode()
                        + " 当前为停用状态（可能在报修），不能占座");
            }
            // 同批内一件资产只保留一条明细：在占/超时未到直接阻止；已释放的也不重新占入，
            // 避免同一资产在同一批次产生两条明细导致计数重复；确需再占请释放后另开新批
            SeatHoldItem sameBatch = itemMapper.selectOne(new LambdaQueryWrapper<SeatHoldItem>()
                    .eq(SeatHoldItem::getBatchId, batch.getId())
                    .eq(SeatHoldItem::getDeskChairId, deskChairId)
                    .orderByDesc(SeatHoldItem::getId)
                    .last("LIMIT 1"));
            if (sameBatch != null) {
                throw new IllegalArgumentException("资产 " + deskChair.getAssetCode()
                        + " 已在本批次中（" + itemStatusText(sameBatch.getItemStatus())
                        + "），同一批次不能重复占住；如需再占请另开新批");
            }

            SeatHoldItem item = new SeatHoldItem();
            item.setBatchId(batch.getId());
            item.setBatchNo(batch.getBatchNo());
            item.setDeskChairId(deskChair.getId());
            item.setAssetCode(deskChair.getAssetCode());
            item.setAreaId(deskChair.getAreaId());
            item.setItemStatus(SeatHoldItem.STATUS_HOLDING);
            item.setPreviousDeskStatus(deskChair.getStatus() == null ? 1 : deskChair.getStatus());
            item.setCreatedAt(now);
            newItems.add(item);
            toDisable.put(deskChair.getId(), deskChair);
        }

        newItems.forEach(itemMapper::insert);
        for (DeskChair deskChair : toDisable.values()) {
            deskChair.setStatus(0);
            deskChair.setUpdatedAt(now);
            deskChairMapper.updateById(deskChair);
        }
    }

    /**
     * 释放明细并恢复桌椅：按占住前状态恢复；若该桌椅存在未闭环报修单则保持停用，
     * 避免恢复动作覆盖报修锁定。
     */
    private void doRelease(SeatHoldBatch batch, SeatHoldItem item, String operator) {
        transactionTemplate.executeWithoutResult(status -> {
            LocalDateTime now = LocalDateTime.now();
            DeskChair deskChair = deskChairMapper.selectById(item.getDeskChairId());
            if (deskChair == null) {
                throw new IllegalArgumentException("资产 " + item.getAssetCode() + " 已不存在，无法释放");
            }
            int targetStatus = item.getPreviousDeskStatus() == null ? 1 : item.getPreviousDeskStatus();
            boolean blockedByRepair = repairOrderMapper.countOpenByDeskChair(deskChair.getId()) > 0;
            if (blockedByRepair) {
                targetStatus = 0;
            }
            deskChair.setStatus(targetStatus);
            deskChair.setUpdatedAt(now);
            deskChairMapper.updateById(deskChair);

            item.setItemStatus(SeatHoldItem.STATUS_RELEASED);
            item.setReleasedBy(operator);
            item.setReleasedAt(now);
            item.setUpdatedAt(now);
            itemMapper.updateById(item);
            refreshCounts(batch);
        });
    }

    private SeatHoldItem findActiveHold(Long deskChairId) {
        List<SeatHoldItem> active = itemMapper.findActiveByDeskChair(deskChairId);
        return active.isEmpty() ? null : active.get(0);
    }

    private SeatHoldBatch requireBatch(Long batchId) {
        if (batchId == null) {
            throw new IllegalArgumentException("缺少占座批次");
        }
        SeatHoldBatch batch = batchMapper.selectById(batchId);
        if (batch == null) {
            throw new IllegalArgumentException("占座批次不存在");
        }
        return batch;
    }

    private void requireOpen(SeatHoldBatch batch) {
        if (SeatHoldBatch.STATUS_ENDED.equals(batch.getStatus())) {
            throw new IllegalArgumentException("占座批次 " + batch.getBatchNo() + " 已结束，禁止再操作");
        }
    }

    private SeatHoldItem requireItem(SeatHoldBatch batch, Long itemId) {
        if (itemId == null) {
            throw new IllegalArgumentException("缺少占座明细");
        }
        SeatHoldItem item = itemMapper.selectById(itemId);
        if (item == null || !batch.getId().equals(item.getBatchId())) {
            throw new IllegalArgumentException("占座明细不存在或不属于该批次");
        }
        return item;
    }

    private void refreshCounts(SeatHoldBatch batch) {
        List<SeatHoldItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<SeatHoldItem>().eq(SeatHoldItem::getBatchId, batch.getId()));
        long held = items.stream().filter(i -> SeatHoldItem.STATUS_HOLDING.equals(i.getItemStatus())).count();
        long timeout = items.stream().filter(i -> SeatHoldItem.STATUS_TIMEOUT.equals(i.getItemStatus())).count();
        long released = items.stream().filter(i -> SeatHoldItem.STATUS_RELEASED.equals(i.getItemStatus())).count();
        batch.setTotalCount(items.size());
        batch.setHeldCount((int) held);
        batch.setTimeoutCount((int) timeout);
        batch.setReleasedCount((int) released);
        batch.setUpdatedAt(LocalDateTime.now());
        batchMapper.updateById(batch);
    }

    private List<Long> normalizeIds(List<Long> rawIds) {
        if (rawIds == null || rawIds.isEmpty()) {
            return List.of();
        }
        Set<Long> distinct = rawIds.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (distinct.size() > MAX_ASSETS) {
            throw new IllegalArgumentException("单批最多占住 " + MAX_ASSETS + " 件资产");
        }
        return new ArrayList<>(distinct);
    }

    private String itemStatusText(String status) {
        return switch (status) {
            case SeatHoldItem.STATUS_HOLDING -> "在占";
            case SeatHoldItem.STATUS_TIMEOUT -> "超时未到";
            case SeatHoldItem.STATUS_RELEASED -> "已释放";
            default -> status;
        };
    }

    private String requireText(String text, String message) {
        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return text.trim();
    }

    private String truncate(String text, int max) {
        if (text == null) {
            return null;
        }
        String trimmed = text.trim();
        return trimmed.length() > max ? trimmed.substring(0, max) : trimmed;
    }

    private String generateBatchNo() {
        return "ZZ" + LocalDateTime.now().format(BATCH_NO_FORMATTER)
                + String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
    }
}
