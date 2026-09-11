package com.example.service;

import com.example.dto.RepairOrderCreateRequest;
import com.example.entity.RepairOrder;

import java.util.List;

public interface RepairOrderService {

    /** 创建报修单：校验桌椅/分区、阻止未闭环重复报修，并将桌椅置停用 */
    RepairOrder create(RepairOrderCreateRequest request);

    /** 列表：可按分区、状态筛选 */
    List<RepairOrder> search(Long areaId, String status);

    /** 详情 */
    RepairOrder findById(Long id);

    /** 接单：待接单 → 维修中，记录处理人与接单时间 */
    RepairOrder accept(Long id, String repairer);

    /** 完结：维修中 → 已修复/无法修复，可同步处置桌椅（恢复可用/转停用） */
    RepairOrder finish(Long id, String result, String repairNote,
                       Integer deskStatusAfter, String operator);

    /** 闭环后处置桌椅：恢复可用(1)/转停用(0) */
    RepairOrder handleDeskStatus(Long id, Integer deskStatusAfter, String operator);
}
