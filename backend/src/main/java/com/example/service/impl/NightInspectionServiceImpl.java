package com.example.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.dto.NightInspectionCheckRequest;
import com.example.dto.NightInspectionCreateRequest;
import com.example.dto.NightInspectionHandleRequest;
import com.example.entity.DeskChair;
import com.example.entity.NightInspectionBatch;
import com.example.entity.NightInspectionItem;
import com.example.entity.ReadingArea;
import com.example.mapper.DeskChairMapper;
import com.example.mapper.NightInspectionBatchMapper;
import com.example.mapper.NightInspectionItemMapper;
import com.example.mapper.ReadingAreaMapper;
import com.example.service.NightInspectionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class NightInspectionServiceImpl implements NightInspectionService {

    private static final DateTimeFormatter BATCH_NO_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    @Autowired
    private NightInspectionBatchMapper batchMapper;

    @Autowired
    private NightInspectionItemMapper itemMapper;

    @Autowired
    private DeskChairMapper deskChairMapper;

    @Autowired
    private ReadingAreaMapper readingAreaMapper;

    private final TransactionTemplate transactionTemplate;

    public NightInspectionServiceImpl(PlatformTransactionManager transactionManager) {
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Override
    public NightInspectionBatch createBatch(NightInspectionCreateRequest request) {
        if (request == null || request.getAreaId() == null) {
            throw new IllegalArgumentException("请选择巡检分区");
        }
        String operator = requireText(request.getOperator(), "操作人不能为空");
        ReadingArea area = readingAreaMapper.selectById(request.getAreaId());
        if (area == null || area.getStatus() == null || area.getStatus() != 1) {
            throw new IllegalArgumentException("巡检分区不存在或已停用");
        }
        // 同一分区只允许存在一个巡检中的批次，避免两批混记同一批桌椅
        List<NightInspectionBatch> openBatches = batchMapper.findOpenByArea(area.getId());
        if (!openBatches.isEmpty()) {
            throw new IllegalArgumentException("该分区存在巡检中的批次 " + openBatches.get(0).getBatchNo()
                    + "，请先结束后再发起巡检");
        }

        // 建批即把该区在册桌椅（含停用）固化为待巡明细，应巡数量不再随在册变动重算
        List<DeskChair> desks = deskChairMapper.selectList(
                new LambdaQueryWrapper<DeskChair>().eq(DeskChair::getAreaId, area.getId()));
        if (desks.isEmpty()) {
            throw new IllegalArgumentException("该分区没有在册桌椅，无需巡检");
        }

        NightInspectionBatch batch = new NightInspectionBatch();
        batch.setBatchNo(generateBatchNo());
        batch.setAreaId(area.getId());
        batch.setTotalCount(desks.size());
        batch.setCheckedCount(0);
        batch.setProblemCount(0);
        batch.setRemark(truncate(request.getRemark(), 500));
        batch.setOperator(operator);
        batch.setStatus(NightInspectionBatch.STATUS_OPEN);
        batch.setCreatedAt(LocalDateTime.now());

        transactionTemplate.executeWithoutResult(status -> {
            batchMapper.insert(batch);
            for (DeskChair desk : desks) {
                NightInspectionItem item = new NightInspectionItem();
                item.setBatchId(batch.getId());
                item.setBatchNo(batch.getBatchNo());
                item.setDeskChairId(desk.getId());
                item.setAssetCode(desk.getAssetCode());
                item.setAreaId(area.getId());
                item.setHasProblem(0);
                item.setCheckStatus(NightInspectionItem.CHECK_PENDING);
                item.setCreatedAt(LocalDateTime.now());
                itemMapper.insert(item);
            }
        });
        return findBatchById(batch.getId());
    }

    @Override
    public NightInspectionBatch checkItem(Long batchId, Long itemId, NightInspectionCheckRequest request) {
        NightInspectionBatch batch = requireBatch(batchId);
        requireOpen(batch);
        NightInspectionItem item = requireItem(batch, itemId);
        String operator = requireText(request == null ? null : request.getOperator(), "操作人不能为空");

        // 灯、插座、桌面三项必须逐件登记，缺任何一项都不能提交该件
        String lightResult = requireResult(request.getLightResult(), "灯", item.getAssetCode());
        String socketResult = requireResult(request.getSocketResult(), "插座", item.getAssetCode());
        String deskSurfaceResult = requireResult(request.getDeskSurfaceResult(), "桌面", item.getAssetCode());

        boolean hasProblem = NightInspectionItem.RESULT_ABNORMAL.equals(lightResult)
                || NightInspectionItem.RESULT_ABNORMAL.equals(socketResult)
                || NightInspectionItem.RESULT_ABNORMAL.equals(deskSurfaceResult);
        String handleOpinion = trimToNull(request.getHandleOpinion());
        if (hasProblem && handleOpinion == null) {
            throw new IllegalArgumentException("该件（资产编号：" + item.getAssetCode()
                    + "）存在灯/插座/桌面问题，请填写处理意见后再提交");
        }

        // updateById 默认 NOT_NULL 策略会跳过 null，重新提交为正常时须显式 set 才能清空旧的处理意见/问题描述
        itemMapper.update(null, new LambdaUpdateWrapper<NightInspectionItem>()
                .eq(NightInspectionItem::getId, item.getId())
                .set(NightInspectionItem::getLightResult, lightResult)
                .set(NightInspectionItem::getSocketResult, socketResult)
                .set(NightInspectionItem::getDeskSurfaceResult, deskSurfaceResult)
                .set(NightInspectionItem::getProblemDetail, truncate(trimToNull(request.getProblemDetail()), 1000))
                .set(NightInspectionItem::getHasProblem, hasProblem ? 1 : 0)
                .set(NightInspectionItem::getHandleOpinion, truncate(handleOpinion, 1000))
                .set(NightInspectionItem::getCheckStatus, NightInspectionItem.CHECK_CHECKED)
                .set(NightInspectionItem::getCheckedBy, operator)
                .set(NightInspectionItem::getCheckedAt, LocalDateTime.now())
                .set(NightInspectionItem::getUpdatedAt, LocalDateTime.now()));

        refreshCounts(batch);
        return findBatchById(batch.getId());
    }

    @Override
    public NightInspectionBatch completeBatch(Long batchId, NightInspectionHandleRequest request) {
        NightInspectionBatch batch = requireBatch(batchId);
        requireOpen(batch);
        requireText(request == null ? null : request.getOperator(), "操作人不能为空");

        List<NightInspectionItem> items = itemMapper.findByBatchIdWithArea(batch.getId());
        long pending = items.stream()
                .filter(i -> NightInspectionItem.CHECK_PENDING.equals(i.getCheckStatus()))
                .count();
        if (pending > 0) {
            throw new IllegalArgumentException("仍有 " + pending
                    + " 件桌椅未巡检，请逐件登记灯、插座和桌面情况后再结束批次");
        }

        // 闭环硬约束：有问题的明细必须都已写明处理意见，提示里直接列出资产编号
        List<String> problemWithoutOpinion = items.stream()
                .filter(i -> Integer.valueOf(1).equals(i.getHasProblem()))
                .filter(i -> i.getHandleOpinion() == null || i.getHandleOpinion().trim().isEmpty())
                .map(NightInspectionItem::getAssetCode)
                .toList();
        if (!problemWithoutOpinion.isEmpty()) {
            throw new IllegalArgumentException("以下 " + problemWithoutOpinion.size()
                    + " 件桌椅有问题但未填写处理意见，请补全后再结束："
                    + String.join("、", problemWithoutOpinion));
        }

        batch.setStatus(NightInspectionBatch.STATUS_COMPLETED);
        batch.setCompletedAt(LocalDateTime.now());
        batch.setUpdatedAt(LocalDateTime.now());
        batchMapper.updateById(batch);
        return findBatchById(batch.getId());
    }

    @Override
    public List<NightInspectionBatch> listBatches(Long areaId, String status) {
        return batchMapper.searchWithArea(areaId, trimToNull(status));
    }

    @Override
    public NightInspectionBatch findBatchById(Long id) {
        NightInspectionBatch batch = batchMapper.findByIdWithArea(id);
        if (batch == null) {
            throw new IllegalArgumentException("巡检批次不存在");
        }
        batch.setItems(itemMapper.findByBatchIdWithArea(id));
        return batch;
    }

    @Override
    public List<NightInspectionItem> searchRecords(Long areaId, Integer hasProblem) {
        if (hasProblem != null && hasProblem != 0 && hasProblem != 1) {
            throw new IllegalArgumentException("是否有问题筛选值不正确");
        }
        return itemMapper.searchRecords(areaId, hasProblem);
    }

    @Override
    public List<NightInspectionItem> findRecordsByDeskChair(Long deskChairId) {
        if (deskChairId == null) {
            throw new IllegalArgumentException("缺少桌椅信息");
        }
        if (deskChairMapper.selectById(deskChairId) == null) {
            throw new IllegalArgumentException("桌椅不存在");
        }
        return itemMapper.findRecordsByDeskChair(deskChairId);
    }

    // ==================== 公共校验与工具 ====================

    private NightInspectionBatch requireBatch(Long batchId) {
        if (batchId == null) {
            throw new IllegalArgumentException("缺少巡检批次");
        }
        NightInspectionBatch batch = batchMapper.selectById(batchId);
        if (batch == null) {
            throw new IllegalArgumentException("巡检批次不存在");
        }
        return batch;
    }

    private void requireOpen(NightInspectionBatch batch) {
        if (NightInspectionBatch.STATUS_COMPLETED.equals(batch.getStatus())) {
            throw new IllegalArgumentException("巡检批次 " + batch.getBatchNo() + " 已结束，禁止再登记");
        }
    }

    private NightInspectionItem requireItem(NightInspectionBatch batch, Long itemId) {
        if (itemId == null) {
            throw new IllegalArgumentException("缺少巡检明细");
        }
        NightInspectionItem item = itemMapper.selectById(itemId);
        if (item == null || !batch.getId().equals(item.getBatchId())) {
            throw new IllegalArgumentException("巡检明细不存在或不属于该批次");
        }
        return item;
    }

    /**
     * 单项巡检结果必填且只能是 NORMAL/ABNORMAL：
     * 灯或插座结果没写不能提交该件，桌面情况同样逐件必填。
     */
    private String requireResult(String result, String partName, String assetCode) {
        String value = trimToNull(result);
        if (value == null) {
            throw new IllegalArgumentException("未登记" + partName + "巡检结果，不能提交该件（资产编号："
                    + assetCode + "）");
        }
        if (!NightInspectionItem.RESULT_NORMAL.equals(value)
                && !NightInspectionItem.RESULT_ABNORMAL.equals(value)) {
            throw new IllegalArgumentException(partName + "巡检结果不正确（资产编号：" + assetCode + "）");
        }
        return value;
    }

    /** 已巡/问题数量按明细实况重算，重复提交同一件不会产生重复计数 */
    private void refreshCounts(NightInspectionBatch batch) {
        batch.setCheckedCount(itemMapper.countChecked(batch.getId()));
        batch.setProblemCount(itemMapper.countProblem(batch.getId()));
        batch.setUpdatedAt(LocalDateTime.now());
        batchMapper.updateById(batch);
    }

    private String requireText(String text, String message) {
        String value = trimToNull(text);
        if (value == null) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    private String trimToNull(String text) {
        if (text == null) {
            return null;
        }
        String trimmed = text.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String truncate(String text, int max) {
        if (text == null) {
            return null;
        }
        return text.length() > max ? text.substring(0, max) : text;
    }

    private String generateBatchNo() {
        return "XJ" + LocalDateTime.now().format(BATCH_NO_FORMATTER)
                + String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
    }
}
