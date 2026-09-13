package com.example.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.dto.StocktakeActualLine;
import com.example.dto.StocktakeCreateRequest;
import com.example.dto.StocktakeHandleRequest;
import com.example.entity.DeskChair;
import com.example.entity.ReadingArea;
import com.example.entity.StocktakeBatch;
import com.example.entity.StocktakeHandleRecord;
import com.example.entity.StocktakeItem;
import com.example.entity.Tag;
import com.example.mapper.DeskChairMapper;
import com.example.mapper.ReadingAreaMapper;
import com.example.mapper.StocktakeBatchMapper;
import com.example.mapper.StocktakeHandleRecordMapper;
import com.example.mapper.StocktakeItemMapper;
import com.example.mapper.TagMapper;
import com.example.service.StocktakeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Service
public class StocktakeServiceImpl implements StocktakeService {

    private static final int MAX_LINES = 1000;
    private static final DateTimeFormatter BATCH_NO_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    @Autowired
    private StocktakeBatchMapper batchMapper;

    @Autowired
    private StocktakeItemMapper itemMapper;

    @Autowired
    private StocktakeHandleRecordMapper recordMapper;

    @Autowired
    private DeskChairMapper deskChairMapper;

    @Autowired
    private ReadingAreaMapper readingAreaMapper;

    @Autowired
    private TagMapper tagMapper;

    private final TransactionTemplate transactionTemplate;

    public StocktakeServiceImpl(PlatformTransactionManager transactionManager) {
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Override
    public StocktakeBatch createBatch(StocktakeCreateRequest request) {
        if (request == null || request.getAreaId() == null) {
            throw new IllegalArgumentException("请选择盘点分区");
        }
        String operator = requireText(request.getOperator(), "操作人不能为空");
        ReadingArea area = readingAreaMapper.selectById(request.getAreaId());
        if (area == null || area.getStatus() == null || area.getStatus() != 1) {
            throw new IllegalArgumentException("盘点分区不存在或已停用");
        }
        // 重复盘点明确阻止：同一分区只允许存在一个盘点中的批次，完成后方可再次发起
        List<StocktakeBatch> openBatches = batchMapper.findOpenByArea(area.getId());
        if (!openBatches.isEmpty()) {
            throw new IllegalArgumentException("该分区存在盘点中的批次 " + openBatches.get(0).getBatchNo()
                    + "，请先完成后再发起盘点");
        }

        // 应盘基数在建批时即按分区在册桌椅数（含停用）固化，列表/详情/后续比对共用同一基数，
        // 不再等到提交实盘时才写入，避免管理员把"未录入"与"分区无在册桌椅"混淆
        int expectedCount = countAreaBookedAssets(area.getId());

        StocktakeBatch batch = new StocktakeBatch();
        batch.setBatchNo(generateBatchNo());
        batch.setAreaId(area.getId());
        batch.setExpectedCount(expectedCount);
        batch.setActualCount(0);
        batch.setCheckedCount(0);
        batch.setDiffCount(0);
        batch.setRemark(truncate(request.getRemark(), 500));
        batch.setOperator(operator);
        batch.setStatus(StocktakeBatch.STATUS_OPEN);
        batch.setCreatedAt(LocalDateTime.now());
        batchMapper.insert(batch);
        return findById(batch.getId());
    }

    @Override
    public StocktakeBatch submitActuals(Long batchId, List<StocktakeActualLine> rawLines,
                                        String operator, String remark) {
        StocktakeBatch batch = requireBatch(batchId);
        requireOpen(batch);
        String op = requireText(operator, "操作人不能为空");
        if (rawLines == null || rawLines.isEmpty()) {
            throw new IllegalArgumentException("请至少录入一条实盘资产");
        }

        // 编号清洗去重：同一次录入/导入中重复编号明确阻止，避免一件资产被盘两次
        Map<String, StocktakeActualLine> lineMap = new LinkedHashMap<>();
        for (StocktakeActualLine raw : rawLines) {
            if (raw == null) {
                continue;
            }
            String code = normalizeCode(raw.getAssetCode());
            if (code == null) {
                throw new IllegalArgumentException("存在未填写资产编号的实盘行");
            }
            if (lineMap.put(code, raw) != null) {
                throw new IllegalArgumentException("实盘资产编号重复：" + code + "，请勿重复盘点");
            }
        }
        if (lineMap.isEmpty()) {
            throw new IllegalArgumentException("请至少录入一条有效实盘资产");
        }
        if (lineMap.size() > MAX_LINES) {
            throw new IllegalArgumentException("单次最多录入 " + MAX_LINES + " 条实盘资产");
        }

        Map<Long, String> areaNameCache = new LinkedHashMap<>();
        Map<Long, String> tagNameCache = new LinkedHashMap<>();

        // 在册全集按编号索引，跨分区错区（盘在本区但在册属别区）也能识别
        Map<String, DeskChair> bookByCode = new LinkedHashMap<>();
        deskChairMapper.selectList(new LambdaQueryWrapper<DeskChair>())
                .forEach(dc -> bookByCode.put(normalizeCode(dc.getAssetCode()), dc));

        List<StocktakeItem> newItems = new ArrayList<>();
        Set<String> matchedCodes = new LinkedHashSet<>();

        // 1) 逐行实盘比对在册
        for (Map.Entry<String, StocktakeActualLine> entry : lineMap.entrySet()) {
            String code = entry.getKey();
            StocktakeActualLine line = entry.getValue();
            Long actualAreaId = line.getActualAreaId() != null ? line.getActualAreaId() : batch.getAreaId();
            validateAreaExists(actualAreaId, areaNameCache);

            StocktakeItem item = new StocktakeItem();
            item.setBatchId(batch.getId());
            item.setBatchNo(batch.getBatchNo());
            item.setAssetCode(code);
            item.setActualAreaId(actualAreaId);
            item.setActualStatus(line.getActualStatus());
            applyActualTags(item, line.getActualTagIds(), tagNameCache);

            DeskChair book = bookByCode.get(code);
            if (book == null) {
                item.setDeskChairId(null);
                item.setDiffType(StocktakeItem.DIFF_SURPLUS);
                item.setDiffDetail("实盘编号在在册资产中不存在，疑似盘盈");
            } else {
                matchedCodes.add(code);
                item.setDeskChairId(book.getId());
                fillBookSnapshot(item, book, areaNameCache, tagNameCache);
                item.setDiffType(classify(book, line, actualAreaId, item, areaNameCache));
            }
            item.setCheckStatus(StocktakeItem.CHECK_PENDING);
            item.setRecheckCount(0);
            item.setCreatedAt(LocalDateTime.now());
            newItems.add(item);
        }

        // 2) 在册但本次未盘到 => 缺失（应盘范围与建批基数同为该分区在册资产，含停用）
        List<DeskChair> expected = listAreaBookedAssets(batch.getAreaId());
        for (DeskChair book : expected) {
            String code = normalizeCode(book.getAssetCode());
            if (matchedCodes.contains(code)) {
                continue;
            }
            StocktakeItem item = new StocktakeItem();
            item.setBatchId(batch.getId());
            item.setBatchNo(batch.getBatchNo());
            item.setDeskChairId(book.getId());
            item.setAssetCode(code);
            fillBookSnapshot(item, book, areaNameCache, tagNameCache);
            // 缺失项没有实盘值
            item.setActualAreaId(null);
            item.setActualStatus(null);
            item.setDiffType(StocktakeItem.DIFF_MISSING);
            item.setDiffDetail("在册资产本次未盘到，疑似缺失");
            item.setCheckStatus(StocktakeItem.CHECK_PENDING);
            item.setRecheckCount(0);
            item.setCreatedAt(LocalDateTime.now());
            newItems.add(item);
        }

        // 明细按差异优先、编号排序，差异项排在前面便于逐项闭环
        newItems.sort(Comparator
                .comparing((StocktakeItem i) -> StocktakeItem.DIFF_MATCH.equals(i.getDiffType()))
                .thenComparing(StocktakeItem::getAssetCode));

        // 应盘数量沿用建批时固化的基数，提交/重新录入均不重算、不覆盖
        int expectedCount = batch.getExpectedCount() == null ? 0 : batch.getExpectedCount();
        int actualCount = lineMap.size();
        int diffCount = (int) newItems.stream()
                .filter(i -> !StocktakeItem.DIFF_MATCH.equals(i.getDiffType())).count();

        transactionTemplate.executeWithoutResult(status -> {
            // 重新录入会替换上一版实盘：旧明细与逐项处理记录随之清除，批次级录入轨迹保留
            itemMapper.deleteByBatchId(batch.getId());
            recordMapper.deleteItemRecordsByBatchId(batch.getId());
            newItems.forEach(itemMapper::insert);

            // 应盘基数建批时已固化，此处只更新实盘/已核/差异，不重写 expectedCount
            batch.setActualCount(actualCount);
            batch.setCheckedCount(0);
            batch.setDiffCount(diffCount);
            batch.setUpdatedAt(LocalDateTime.now());
            batchMapper.updateById(batch);

            StocktakeHandleRecord record = new StocktakeHandleRecord();
            record.setBatchId(batch.getId());
            record.setItemId(null);
            record.setAction(StocktakeHandleRecord.ACTION_SUBMIT);
            record.setOpinion(buildSubmitOpinion(expectedCount, actualCount, diffCount, remark));
            record.setOperator(op);
            record.setCreatedAt(LocalDateTime.now());
            recordMapper.insert(record);
        });

        return findById(batch.getId());
    }

    @Override
    public StocktakeBatch confirmItem(Long batchId, Long itemId, StocktakeHandleRequest request) {
        StocktakeBatch batch = requireBatch(batchId);
        requireOpen(batch);
        StocktakeItem item = requireItem(batch, itemId);
        String operator = requireText(request == null ? null : request.getOperator(), "操作人不能为空");
        if (StocktakeItem.CHECK_CONFIRMED.equals(item.getCheckStatus())) {
            throw new IllegalArgumentException("该明细已确认，请先重新复核再修改");
        }

        String recordOpinion;
        if (StocktakeItem.DIFF_MISSING.equals(item.getDiffType())) {
            // 在册未盘到的缺失项必须写明缺失原因，作为批次完成的硬性前置
            String missingReason = requireText(request.getMissingReason(),
                    "请填写缺失原因后再确认（资产编号：" + item.getAssetCode() + "）");
            missingReason = truncate(missingReason, 1000);
            item.setMissingReason(missingReason);
            // 缺失项不另设处理意见录入入口，留痕意见由缺失原因生成，保证逐项记录可追溯
            String opinion = request.getHandleOpinion() == null || request.getHandleOpinion().trim().isEmpty()
                    ? "缺失原因：" + missingReason : truncate(request.getHandleOpinion(), 1000);
            item.setHandleOpinion(opinion);
            recordOpinion = opinion;
        } else {
            String opinion = requireText(request.getHandleOpinion(), "请填写处理意见后再确认");
            item.setHandleOpinion(truncate(opinion, 1000));
            item.setMissingReason(null);
            recordOpinion = opinion;
        }

        item.setCheckStatus(StocktakeItem.CHECK_CONFIRMED);
        item.setConfirmedBy(operator);
        item.setConfirmedAt(LocalDateTime.now());
        item.setUpdatedAt(LocalDateTime.now());
        itemMapper.updateById(item);
        insertItemRecord(batch, item, StocktakeHandleRecord.ACTION_CONFIRM, recordOpinion, operator);
        refreshCheckedCount(batch);
        return findById(batch.getId());
    }

    @Override
    public StocktakeBatch recheckItem(Long batchId, Long itemId, StocktakeHandleRequest request) {
        StocktakeBatch batch = requireBatch(batchId);
        requireOpen(batch);
        StocktakeItem item = requireItem(batch, itemId);
        String operator = requireText(request == null ? null : request.getOperator(), "操作人不能为空");
        if (!StocktakeItem.CHECK_CONFIRMED.equals(item.getCheckStatus())) {
            throw new IllegalArgumentException("仅已确认的明细可以重新复核");
        }

        String recheckOpinion = request.getHandleOpinion() == null
                || request.getHandleOpinion().trim().isEmpty()
                ? "管理员发起重新复核" : request.getHandleOpinion().trim();

        // updateById 默认 NOT_NULL 策略会跳过 null，须显式 set 才能清空原确认意见/缺失原因/确认人/确认时间
        item.setCheckStatus(StocktakeItem.CHECK_PENDING);
        item.setRecheckCount((item.getRecheckCount() == null ? 0 : item.getRecheckCount()) + 1);
        item.setHandleOpinion(null);
        item.setMissingReason(null);
        item.setConfirmedBy(null);
        item.setConfirmedAt(null);
        item.setUpdatedAt(LocalDateTime.now());
        itemMapper.update(null, new LambdaUpdateWrapper<StocktakeItem>()
                .eq(StocktakeItem::getId, item.getId())
                .set(StocktakeItem::getCheckStatus, StocktakeItem.CHECK_PENDING)
                .set(StocktakeItem::getRecheckCount, item.getRecheckCount())
                .set(StocktakeItem::getHandleOpinion, null)
                .set(StocktakeItem::getMissingReason, null)
                .set(StocktakeItem::getConfirmedBy, null)
                .set(StocktakeItem::getConfirmedAt, null)
                .set(StocktakeItem::getUpdatedAt, LocalDateTime.now()));
        insertItemRecord(batch, item, StocktakeHandleRecord.ACTION_RECHECK, recheckOpinion, operator);
        refreshCheckedCount(batch);
        return findById(batch.getId());
    }

    @Override
    public StocktakeBatch completeBatch(Long batchId, StocktakeHandleRequest request) {
        StocktakeBatch batch = requireBatch(batchId);
        requireOpen(batch);
        String operator = requireText(request == null ? null : request.getOperator(), "操作人不能为空");

        List<StocktakeItem> items = itemMapper.findByBatchIdWithArea(batch.getId());
        if (items.isEmpty()) {
            throw new IllegalArgumentException("尚未录入实盘，无法完成盘点");
        }
        long pending = items.stream().filter(i -> StocktakeItem.CHECK_PENDING.equals(i.getCheckStatus())).count();
        if (pending > 0) {
            throw new IllegalArgumentException("仍有 " + pending + " 条明细待核，请逐项确认后再完成批次");
        }

        // 闭环硬约束：所有在册未盘到的缺失行都必须写明缺失原因，提示里直接列出还缺原因的资产编号
        List<String> missingWithoutReason = items.stream()
                .filter(i -> StocktakeItem.DIFF_MISSING.equals(i.getDiffType()))
                .filter(i -> i.getMissingReason() == null || i.getMissingReason().trim().isEmpty())
                .map(StocktakeItem::getAssetCode)
                .toList();
        if (!missingWithoutReason.isEmpty()) {
            throw new IllegalArgumentException("以下 " + missingWithoutReason.size()
                    + " 件在册资产未盘到且未填写缺失原因，请补全后再完成："
                    + String.join("、", missingWithoutReason));
        }

        batch.setStatus(StocktakeBatch.STATUS_COMPLETED);
        batch.setCompletedAt(LocalDateTime.now());
        batch.setUpdatedAt(LocalDateTime.now());
        batchMapper.updateById(batch);

        StocktakeHandleRecord record = new StocktakeHandleRecord();
        record.setBatchId(batch.getId());
        record.setItemId(null);
        record.setAction(StocktakeHandleRecord.ACTION_COMPLETE);
        record.setOpinion(buildCompleteOpinion(batch, request == null ? null : request.getHandleOpinion()));
        record.setOperator(operator);
        record.setCreatedAt(LocalDateTime.now());
        recordMapper.insert(record);
        return findById(batch.getId());
    }

    @Override
    public List<StocktakeBatch> findAll() {
        return batchMapper.findAllWithArea();
    }

    @Override
    public StocktakeBatch findById(Long id) {
        StocktakeBatch batch = batchMapper.findByIdWithArea(id);
        if (batch == null) {
            throw new IllegalArgumentException("盘点批次不存在");
        }
        List<StocktakeItem> items = itemMapper.findByBatchIdWithArea(id);
        List<StocktakeHandleRecord> allRecords = recordMapper.findByBatchId(id);
        Map<Long, List<StocktakeHandleRecord>> recordsByItem = allRecords.stream()
                .filter(r -> r.getItemId() != null)
                .collect(Collectors.groupingBy(StocktakeHandleRecord::getItemId));
        items.forEach(item -> item.setRecords(recordsByItem.getOrDefault(item.getId(), List.of())));
        batch.setItems(items);
        batch.setRecords(allRecords.stream().filter(r -> r.getItemId() == null).toList());
        return batch;
    }

    // ==================== 差异比对 ====================

    /**
     * 按优先级判定单项差异：错区 > 停用不符 > 标签不符 > 一致。
     */
    private String classify(DeskChair book, StocktakeActualLine line, Long actualAreaId,
                            StocktakeItem item, Map<Long, String> areaNameCache) {
        List<String> details = new ArrayList<>();

        boolean wrongArea = !Objects.equals(book.getAreaId(), actualAreaId);
        if (wrongArea) {
            details.add("实盘分区为「" + areaName(actualAreaId, areaNameCache)
                    + "」，在册分区为「" + areaName(book.getAreaId(), areaNameCache) + "」");
        }

        Integer actualStatus = line.getActualStatus() != null ? line.getActualStatus()
                : normalizeStatus(book.getStatus());
        item.setActualStatus(actualStatus);
        Integer bookStatus = normalizeStatus(book.getStatus());
        boolean statusMismatch = !Objects.equals(bookStatus, actualStatus);
        if (statusMismatch) {
            details.add("启用状态不符：在册为「" + statusText(bookStatus)
                    + "」，实盘为「" + statusText(actualStatus) + "」");
        }

        boolean tagMismatch = false;
        // actualTagIds 为 null 表示本次未核对标签（包括未选择标签的兜底场景），不参与差异判断
        if (line.getActualTagIds() != null) {
            tagMismatch = !Objects.equals(item.getBookTagIds(), item.getActualTagIds());
            if (tagMismatch) {
                details.add("标签不符：在册为「" + emptyTagHint(item.getBookTagNames())
                        + "」，实盘为「" + emptyTagHint(item.getActualTagNames()) + "」");
            }
        }

        if (wrongArea) {
            item.setDiffType(StocktakeItem.DIFF_WRONG_AREA);
        } else if (statusMismatch) {
            item.setDiffType(StocktakeItem.DIFF_STATUS_MISMATCH);
        } else if (tagMismatch) {
            item.setDiffType(StocktakeItem.DIFF_TAG_MISMATCH);
        } else {
            item.setDiffType(StocktakeItem.DIFF_MATCH);
        }
        item.setDiffDetail(details.isEmpty() ? "编号、分区、启用状态与标签均一致" : String.join("；", details));
        return item.getDiffType();
    }

    private void fillBookSnapshot(StocktakeItem item, DeskChair book,
                                  Map<Long, String> areaNameCache, Map<Long, String> tagNameCache) {
        item.setBookAreaId(book.getAreaId());
        item.setBookStatus(normalizeStatus(book.getStatus()));
        List<Tag> tags = tagMapper.findByDeskChairId(book.getId());
        List<Long> tagIds = tags.stream().map(Tag::getId).sorted().toList();
        item.setBookTagIds(joinIds(tagIds));
        item.setBookTagNames(tags.stream().map(Tag::getTagName)
                .collect(Collectors.joining("、")));
        areaNameCache.computeIfAbsent(book.getAreaId(), key -> {
            ReadingArea area = readingAreaMapper.selectById(key);
            return area == null ? null : area.getAreaName();
        });
        tags.forEach(t -> tagNameCache.putIfAbsent(t.getId(), t.getTagName()));
    }

    private void applyActualTags(StocktakeItem item, List<Long> actualTagIds,
                                 Map<Long, String> tagNameCache) {
        if (actualTagIds == null) {
            // 未核对标签：不写入 actual_tag_ids，比对时跳过
            item.setActualTagIds(null);
            item.setActualTagNames(null);
            return;
        }
        List<Long> distinctIds = actualTagIds.stream().filter(Objects::nonNull)
                .distinct().sorted().toList();
        for (Long tagId : distinctIds) {
            if (tagMapper.selectById(tagId) == null) {
                throw new IllegalArgumentException("实盘标签不存在（ID：" + tagId + "）");
            }
        }
        item.setActualTagIds(joinIds(distinctIds));
        item.setActualTagNames(distinctIds.stream()
                .map(id -> tagNameCache.computeIfAbsent(id, key -> {
                    Tag tag = tagMapper.selectById(key);
                    return tag == null ? null : tag.getTagName();
                }))
                .filter(Objects::nonNull)
                .collect(Collectors.joining("、")));
    }

    // ==================== 公共校验与工具 ====================

    /**
     * 盘点分区在册桌椅全集（含停用），建批应盘基数与提交时缺失项生成共用同一口径。
     */
    private List<DeskChair> listAreaBookedAssets(Long areaId) {
        return deskChairMapper.selectList(
                new LambdaQueryWrapper<DeskChair>().eq(DeskChair::getAreaId, areaId));
    }

    /**
     * 盘点分区在册桌椅数（含停用），作为建批时固化的应盘基数。
     */
    private int countAreaBookedAssets(Long areaId) {
        return listAreaBookedAssets(areaId).size();
    }

    private StocktakeBatch requireBatch(Long batchId) {
        if (batchId == null) {
            throw new IllegalArgumentException("缺少盘点批次");
        }
        StocktakeBatch batch = batchMapper.selectById(batchId);
        if (batch == null) {
            throw new IllegalArgumentException("盘点批次不存在");
        }
        return batch;
    }

    private void requireOpen(StocktakeBatch batch) {
        if (StocktakeBatch.STATUS_COMPLETED.equals(batch.getStatus())) {
            throw new IllegalArgumentException("盘点批次 " + batch.getBatchNo() + " 已完成，禁止再操作");
        }
    }

    private StocktakeItem requireItem(StocktakeBatch batch, Long itemId) {
        if (itemId == null) {
            throw new IllegalArgumentException("缺少盘点明细");
        }
        StocktakeItem item = itemMapper.selectById(itemId);
        if (item == null || !batch.getId().equals(item.getBatchId())) {
            throw new IllegalArgumentException("盘点明细不存在或不属于该批次");
        }
        return item;
    }

    private void validateAreaExists(Long areaId, Map<Long, String> areaNameCache) {
        if (areaNameCache.computeIfAbsent(areaId, key -> {
            ReadingArea area = readingAreaMapper.selectById(key);
            return area == null ? null : area.getAreaName();
        }) == null) {
            throw new IllegalArgumentException("实盘分区不存在（ID：" + areaId + "）");
        }
    }

    private void insertItemRecord(StocktakeBatch batch, StocktakeItem item,
                                  String action, String opinion, String operator) {
        StocktakeHandleRecord record = new StocktakeHandleRecord();
        record.setBatchId(batch.getId());
        record.setItemId(item.getId());
        record.setAction(action);
        record.setOpinion(truncate(opinion, 1000));
        record.setOperator(operator);
        record.setCreatedAt(LocalDateTime.now());
        recordMapper.insert(record);
    }

    private void refreshCheckedCount(StocktakeBatch batch) {
        batch.setCheckedCount(itemMapper.countConfirmed(batch.getId()));
        batch.setUpdatedAt(LocalDateTime.now());
        batchMapper.updateById(batch);
    }

    private String buildSubmitOpinion(int expectedCount, int actualCount, int diffCount, String remark) {
        String base = "录入实盘：应盘 " + expectedCount + " 件，实盘 " + actualCount
                + " 件，差异 " + diffCount + " 项";
        if (remark != null && !remark.trim().isEmpty()) {
            base += "；说明：" + remark.trim();
        }
        return truncate(base, 1000);
    }

    private String buildCompleteOpinion(StocktakeBatch batch, String opinion) {
        String base = "批次完成：应盘 " + batch.getExpectedCount() + " 件，实盘 " + batch.getActualCount()
                + " 件，差异 " + batch.getDiffCount() + " 项，已全部逐项确认闭环";
        if (opinion != null && !opinion.trim().isEmpty()) {
            base += "；" + opinion.trim();
        }
        return truncate(base, 1000);
    }

    private String areaName(Long areaId, Map<Long, String> cache) {
        if (areaId == null) {
            return "未分区";
        }
        String name = cache.get(areaId);
        return name == null ? "未知分区" : name;
    }

    private String emptyTagHint(String tagNames) {
        return tagNames == null || tagNames.isEmpty() ? "无标签" : tagNames;
    }

    private String statusText(Integer status) {
        return Integer.valueOf(1).equals(status) ? "启用" : "停用";
    }

    private Integer normalizeStatus(Integer status) {
        return status == null ? 1 : status;
    }

    private String joinIds(List<Long> ids) {
        return ids.isEmpty() ? "" : ids.stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    private String normalizeCode(String code) {
        if (code == null) {
            return null;
        }
        String trimmed = code.trim();
        return trimmed.isEmpty() ? null : trimmed;
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
        return "PD" + LocalDateTime.now().format(BATCH_NO_FORMATTER)
                + String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
    }
}
