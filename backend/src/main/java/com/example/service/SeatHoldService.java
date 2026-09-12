package com.example.service;

import com.example.dto.SeatHoldCreateRequest;
import com.example.dto.SeatHoldHandleRequest;
import com.example.dto.SeatHoldHoldRequest;
import com.example.entity.SeatHoldBatch;
import com.example.entity.SeatHoldItem;
import com.example.vo.SeatHoldClearingVO;

import java.util.List;

public interface SeatHoldService {

    /** 值班员选分区和时段开一批，并从桌椅档案勾选资产占住（资产即时置停用） */
    SeatHoldBatch createBatch(SeatHoldCreateRequest request);

    /** 批次列表，按分区和批次状态筛选 */
    List<SeatHoldBatch> search(Long areaId, String status);

    /** 批次详情（含每条占座明细与桌椅实时状态） */
    SeatHoldBatch findById(Long id);

    /** 当前进行中批次正在占住的资产，供开批勾选时禁选/提示，areaId 为空查全部 */
    List<SeatHoldItem> findOpenHolds(Long areaId);

    /** 进行中批次追加占住资产 */
    SeatHoldBatch hold(Long batchId, SeatHoldHoldRequest request);

    /** 当场释放：仅在占（HOLDING）明细可释放，桌椅恢复占住前状态 */
    SeatHoldBatch release(Long batchId, Long itemId, SeatHoldHandleRequest request);

    /** 改超时未到：仅在占（HOLDING）明细可标记，桌椅继续停用，等待整批结束后处置 */
    SeatHoldBatch markTimeout(Long batchId, Long itemId, SeatHoldHandleRequest request);

    /** 超时未到后人员到场，撤回超时标记回到在占（仅进行中批次） */
    SeatHoldBatch revertTimeout(Long batchId, Long itemId, SeatHoldHandleRequest request);

    /** 整批结束：仍占着的资产（HOLDING/TIMEOUT）保持停用，已释放的维持可用 */
    SeatHoldBatch finishBatch(Long batchId, SeatHoldHandleRequest request);

    /** 已结束批次遗留的停用资产，清场确认后逐条释放恢复 */
    SeatHoldBatch releaseLegacy(Long batchId, Long itemId, SeatHoldHandleRequest request);

    /** 已结束批次的清场清单：仍停用桌椅按 超时未到/应恢复/报修停用 分组 */
    SeatHoldClearingVO getClearing(Long batchId);
}
