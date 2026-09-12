package com.example.service.impl;

import com.example.dto.LostItemClaimRequest;
import com.example.dto.LostItemCreateRequest;
import com.example.entity.DeskChair;
import com.example.entity.LostItem;
import com.example.entity.ReadingArea;
import com.example.mapper.DeskChairMapper;
import com.example.mapper.LostItemMapper;
import com.example.mapper.ReadingAreaMapper;
import com.example.service.LostItemService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class LostItemServiceImpl implements LostItemService {

    private static final DateTimeFormatter ITEM_NO_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    @Autowired
    private LostItemMapper lostItemMapper;

    @Autowired
    private DeskChairMapper deskChairMapper;

    @Autowired
    private ReadingAreaMapper readingAreaMapper;

    @Override
    @Transactional
    public LostItem create(LostItemCreateRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("登记内容不能为空");
        }
        Long areaId = request.getAreaId();
        if (areaId == null) {
            throw new IllegalArgumentException("请选择所属分区");
        }
        Long deskChairId = request.getDeskChairId();
        if (deskChairId == null) {
            throw new IllegalArgumentException("请选择对应桌椅");
        }
        String itemName = requireText(request.getItemName(), "请填写物品名称");
        String storageLocation = requireText(request.getStorageLocation(), "请填写暂存位置");
        String operator = requireText(request.getOperator(), "请填写登记值班员");

        ReadingArea area = readingAreaMapper.selectById(areaId);
        if (area == null) {
            throw new IllegalArgumentException("所属分区不存在");
        }
        DeskChair deskChair = deskChairMapper.selectById(deskChairId);
        if (deskChair == null) {
            throw new IllegalArgumentException("对应桌椅不存在");
        }
        if (!deskChair.getAreaId().equals(areaId)) {
            throw new IllegalArgumentException("所选分区与桌椅当前所属分区不一致，请重新选择");
        }

        LostItem item = new LostItem();
        item.setItemNo(generateItemNo());
        item.setAreaId(areaId);
        item.setDeskChairId(deskChairId);
        item.setAssetCode(deskChair.getAssetCode());
        item.setItemName(truncate(itemName, 200));
        item.setStorageLocation(truncate(storageLocation, 200));
        item.setRemark(truncate(request.getRemark(), 500));
        item.setStatus(LostItem.STATUS_PENDING);
        item.setFoundBy(operator);
        item.setCreatedAt(LocalDateTime.now());
        lostItemMapper.insert(item);
        return findById(item.getId());
    }

    @Override
    public List<LostItem> search(Long areaId, String status) {
        if (status != null) {
            status = status.trim();
            if (status.isEmpty()) {
                status = null;
            } else if (!LostItem.STATUS_PENDING.equals(status) && !LostItem.STATUS_CLAIMED.equals(status)) {
                throw new IllegalArgumentException("领取状态不合法");
            }
        }
        return lostItemMapper.search(areaId, status);
    }

    @Override
    public LostItem findById(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("缺少遗失登记单");
        }
        LostItem item = lostItemMapper.findDetailById(id);
        if (item == null) {
            throw new IllegalArgumentException("遗失登记单不存在");
        }
        return item;
    }

    @Override
    @Transactional
    public LostItem claim(Long id, LostItemClaimRequest request) {
        if (id == null) {
            throw new IllegalArgumentException("缺少遗失登记单");
        }
        LostItem item = lostItemMapper.selectById(id);
        if (item == null) {
            throw new IllegalArgumentException("遗失登记单不存在");
        }
        if (!LostItem.STATUS_PENDING.equals(item.getStatus())) {
            throw new IllegalArgumentException("遗失单 " + item.getItemNo() + " 已领取闭环，不能重复领取");
        }
        // 领取必须核验领取人并填写领取结论，缺一不可闭环
        String claimerName = requireText(request == null ? null : request.getClaimerName(), "请填写领取人");
        String claimerVerify = requireText(request == null ? null : request.getClaimerVerify(),
                "请填写领取人核验信息（证件号/学工号等）");
        String claimConclusion = requireText(request == null ? null : request.getClaimConclusion(), "请填写领取结论");
        String operator = requireText(request == null ? null : request.getOperator(), "请填写经办值班员");

        item.setStatus(LostItem.STATUS_CLAIMED);
        item.setClaimerName(claimerName);
        item.setClaimerVerify(truncate(claimerVerify, 200));
        item.setClaimConclusion(truncate(claimConclusion, 500));
        item.setClaimedBy(operator);
        item.setClaimedAt(LocalDateTime.now());
        item.setUpdatedAt(LocalDateTime.now());
        lostItemMapper.updateById(item);
        return findById(id);
    }

    @Override
    public List<LostItem> findPendingByArea(Long areaId) {
        if (areaId == null) {
            throw new IllegalArgumentException("请选择所属分区");
        }
        return lostItemMapper.findPendingByAreaId(areaId);
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

    private String generateItemNo() {
        return "YW" + LocalDateTime.now().format(ITEM_NO_FORMATTER)
                + String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
    }
}
