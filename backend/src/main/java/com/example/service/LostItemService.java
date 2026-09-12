package com.example.service;

import com.example.dto.LostItemClaimRequest;
import com.example.dto.LostItemCreateRequest;
import com.example.entity.LostItem;

import java.util.List;

public interface LostItemService {

    /** 值班员按分区登记桌椅旁捡到的遗失物品，登记后进入待领取状态 */
    LostItem create(LostItemCreateRequest request);

    /** 遗失列表：按分区和是否待领（PENDING/CLAIMED）筛选 */
    List<LostItem> search(Long areaId, String status);

    LostItem findById(Long id);

    /** 领取闭环：必须核验领取人并填写领取结论 */
    LostItem claim(Long id, LostItemClaimRequest request);

    /** 分区内仍待领取的遗失物品，供占座勾选页禁选标记 */
    List<LostItem> findPendingByArea(Long areaId);
}
