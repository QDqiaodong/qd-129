package com.example.service.impl;

import com.example.dto.RepairOrderCreateRequest;
import com.example.entity.DeskChair;
import com.example.entity.ReadingArea;
import com.example.entity.RepairOrder;
import com.example.mapper.DeskChairMapper;
import com.example.mapper.ReadingAreaMapper;
import com.example.mapper.RepairOrderMapper;
import com.example.service.RepairOrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class RepairOrderServiceImpl implements RepairOrderService {

    private static final DateTimeFormatter ORDER_NO_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final Set<String> VALID_URGENCY = Set.of(
            RepairOrder.URGENCY_LOW, RepairOrder.URGENCY_NORMAL,
            RepairOrder.URGENCY_HIGH, RepairOrder.URGENCY_URGENT);

    @Autowired
    private RepairOrderMapper repairOrderMapper;

    @Autowired
    private DeskChairMapper deskChairMapper;

    @Autowired
    private ReadingAreaMapper readingAreaMapper;

    @Override
    @Transactional
    public RepairOrder create(RepairOrderCreateRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("报修内容不能为空");
        }
        Long deskChairId = request.getDeskChairId();
        if (deskChairId == null) {
            throw new IllegalArgumentException("请选择报修桌椅");
        }
        Long areaId = request.getAreaId();
        if (areaId == null) {
            throw new IllegalArgumentException("请选择所属分区");
        }
        String damagePart = requireText(request.getDamagePart(), "请填写损坏部位");
        String phenomenon = requireText(request.getPhenomenon(), "请填写损坏现象");
        String reporter = requireText(request.getReporter(), "请填写报修人");

        String urgency = request.getUrgency();
        if (urgency == null || urgency.trim().isEmpty()) {
            urgency = RepairOrder.URGENCY_NORMAL;
        }
        urgency = urgency.trim();
        if (!VALID_URGENCY.contains(urgency)) {
            throw new IllegalArgumentException("紧急程度不合法");
        }

        DeskChair deskChair = deskChairMapper.selectById(deskChairId);
        if (deskChair == null) {
            throw new IllegalArgumentException("报修桌椅不存在");
        }
        ReadingArea area = readingAreaMapper.selectById(areaId);
        if (area == null) {
            throw new IllegalArgumentException("所属分区不存在");
        }
        if (!deskChair.getAreaId().equals(areaId)) {
            throw new IllegalArgumentException("所选分区与桌椅当前所属分区不一致，请重新选择");
        }

        // 未闭环（待接单/维修中）的工单禁止再次报修，闭环后按结果处置完才能重新发起
        if (repairOrderMapper.countOpenByDeskChair(deskChairId) > 0) {
            throw new IllegalArgumentException("该桌椅存在未闭环的报修单，请处理完成后再报");
        }

        RepairOrder order = new RepairOrder();
        order.setOrderNo(generateOrderNo());
        order.setDeskChairId(deskChairId);
        order.setAreaId(areaId);
        order.setDamagePart(truncate(damagePart, 100));
        order.setUrgency(urgency);
        order.setPhenomenon(truncate(phenomenon, 1000));
        order.setStatus(RepairOrder.STATUS_PENDING);
        order.setReporter(reporter);
        order.setCreatedAt(LocalDateTime.now());
        repairOrderMapper.insert(order);

        // 报修即锁定：桌椅置停用，避免损坏资产继续被使用，修复处置后再恢复
        if (!Integer.valueOf(0).equals(deskChair.getStatus())) {
            deskChair.setStatus(0);
            deskChair.setUpdatedAt(LocalDateTime.now());
            deskChairMapper.updateById(deskChair);
        }

        return findById(order.getId());
    }

    @Override
    public List<RepairOrder> search(Long areaId, String status) {
        if (status != null) {
            status = status.trim();
            if (status.isEmpty()) {
                status = null;
            } else if (!isValidStatus(status)) {
                throw new IllegalArgumentException("工单状态不合法");
            }
        }
        return repairOrderMapper.search(areaId, status);
    }

    @Override
    public RepairOrder findById(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("缺少报修单");
        }
        RepairOrder order = repairOrderMapper.findDetailById(id);
        if (order == null) {
            throw new IllegalArgumentException("报修单不存在");
        }
        return order;
    }

    @Override
    @Transactional
    public RepairOrder accept(Long id, String repairer) {
        RepairOrder order = requireOrder(id);
        if (!RepairOrder.STATUS_PENDING.equals(order.getStatus())) {
            throw new IllegalArgumentException("仅待接单工单可以接单，当前状态：" + statusText(order.getStatus()));
        }
        order.setRepairer(requireText(repairer, "请填写处理人后再接单"));
        order.setStatus(RepairOrder.STATUS_IN_PROGRESS);
        order.setAcceptAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());
        repairOrderMapper.updateById(order);
        return findById(id);
    }

    @Override
    @Transactional
    public RepairOrder finish(Long id, String result, String repairNote,
                              Integer deskStatusAfter, String operator) {
        RepairOrder order = requireOrder(id);
        if (!RepairOrder.STATUS_IN_PROGRESS.equals(order.getStatus())) {
            throw new IllegalArgumentException("仅维修中工单可以完结，当前状态：" + statusText(order.getStatus()));
        }
        String target;
        if (RepairOrder.STATUS_FIXED.equals(result) || RepairOrder.STATUS_UNFIXABLE.equals(result)) {
            target = result;
        } else {
            throw new IllegalArgumentException("完结结果须为已修复或无法修复");
        }
        String note = repairNote == null ? null : repairNote.trim();
        if (RepairOrder.STATUS_UNFIXABLE.equals(target)) {
            if (note == null || note.isEmpty()) {
                throw new IllegalArgumentException("判定无法修复时请填写原因");
            }
        }

        order.setStatus(target);
        order.setFinishAt(LocalDateTime.now());
        order.setRepairNote(truncate(note, 1000));
        order.setUpdatedAt(LocalDateTime.now());
        repairOrderMapper.updateById(order);

        // 完结时可一并处置桌椅；未选择则保持停用，稍后在详情中处置
        if (deskStatusAfter != null) {
            applyDeskStatus(order, deskStatusAfter, operator == null ? order.getRepairer() : operator);
        }
        return findById(id);
    }

    @Override
    @Transactional
    public RepairOrder handleDeskStatus(Long id, Integer deskStatusAfter, String operator) {
        RepairOrder order = requireOrder(id);
        if (!RepairOrder.STATUS_FIXED.equals(order.getStatus())
                && !RepairOrder.STATUS_UNFIXABLE.equals(order.getStatus())) {
            throw new IllegalArgumentException("工单闭环后才能处置桌椅状态");
        }
        applyDeskStatus(order, deskStatusAfter, operator);
        return findById(id);
    }

    // ==================== 内部方法 ====================

    private void applyDeskStatus(RepairOrder order, Integer deskStatusAfter, String operator) {
        if (!Integer.valueOf(1).equals(deskStatusAfter) && !Integer.valueOf(0).equals(deskStatusAfter)) {
            throw new IllegalArgumentException("桌椅处置须为恢复可用或转停用");
        }
        // 仅已修复工单允许恢复可用，无法修复只能转停用
        if (Integer.valueOf(1).equals(deskStatusAfter)
                && RepairOrder.STATUS_UNFIXABLE.equals(order.getStatus())) {
            throw new IllegalArgumentException("无法修复的桌椅不能恢复可用，只能转停用");
        }
        String op = operator == null || operator.trim().isEmpty()
                ? order.getRepairer() : operator.trim();
        if (op == null || op.isEmpty()) {
            throw new IllegalArgumentException("请填写处置人");
        }

        DeskChair deskChair = deskChairMapper.selectById(order.getDeskChairId());
        if (deskChair == null) {
            throw new IllegalArgumentException("工单关联的桌椅已不存在");
        }
        deskChair.setStatus(deskStatusAfter);
        deskChair.setUpdatedAt(LocalDateTime.now());
        deskChairMapper.updateById(deskChair);

        order.setDeskStatusAfter(deskStatusAfter);
        order.setHandledBy(op);
        order.setHandledAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());
        repairOrderMapper.updateById(order);
    }

    private RepairOrder requireOrder(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("缺少报修单");
        }
        RepairOrder order = repairOrderMapper.selectById(id);
        if (order == null) {
            throw new IllegalArgumentException("报修单不存在");
        }
        return order;
    }

    private boolean isValidStatus(String status) {
        return RepairOrder.STATUS_PENDING.equals(status)
                || RepairOrder.STATUS_IN_PROGRESS.equals(status)
                || RepairOrder.STATUS_FIXED.equals(status)
                || RepairOrder.STATUS_UNFIXABLE.equals(status);
    }

    private String statusText(String status) {
        return switch (status) {
            case RepairOrder.STATUS_PENDING -> "待接单";
            case RepairOrder.STATUS_IN_PROGRESS -> "维修中";
            case RepairOrder.STATUS_FIXED -> "已修复";
            case RepairOrder.STATUS_UNFIXABLE -> "无法修复";
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

    private String generateOrderNo() {
        return "BX" + LocalDateTime.now().format(ORDER_NO_FORMATTER)
                + String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
    }
}
