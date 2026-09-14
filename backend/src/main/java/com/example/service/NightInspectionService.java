package com.example.service;

import com.example.dto.NightInspectionCheckRequest;
import com.example.dto.NightInspectionCreateRequest;
import com.example.dto.NightInspectionHandleRequest;
import com.example.entity.NightInspectionBatch;
import com.example.entity.NightInspectionItem;

import java.util.List;

public interface NightInspectionService {

    /** 值班员按阅览区开夜间巡检批次：把该区在册桌椅（含停用）固化为待巡明细 */
    NightInspectionBatch createBatch(NightInspectionCreateRequest request);

    /**
     * 逐件登记灯、插座和桌面情况：灯或插座结果未写不能提交该件；
     * 任一项异常即为有问题，必须写处理意见。
     */
    NightInspectionBatch checkItem(Long batchId, Long itemId, NightInspectionCheckRequest request);

    /** 结束批次：批次内还有未巡明细时不允许结束 */
    NightInspectionBatch completeBatch(Long batchId, NightInspectionHandleRequest request);

    /** 批次列表：可按分区和状态筛选 */
    List<NightInspectionBatch> listBatches(Long areaId, String status);

    NightInspectionBatch findBatchById(Long id);

    /** 巡检记录列表：按分区和是否有问题筛出已逐件登记的明细 */
    List<NightInspectionItem> searchRecords(Long areaId, Integer hasProblem);

    /** 同一件桌椅的全部巡检记录（跨批次），点开记录可查看 */
    List<NightInspectionItem> findRecordsByDeskChair(Long deskChairId);
}
