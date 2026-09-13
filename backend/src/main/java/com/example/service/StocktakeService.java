package com.example.service;

import com.example.dto.StocktakeActualLine;
import com.example.dto.StocktakeCreateRequest;
import com.example.dto.StocktakeHandleRequest;
import com.example.entity.StocktakeBatch;

import java.util.List;

public interface StocktakeService {

    /** 创建盘点批次；同分区存在盘点中的批次时明确阻止 */
    StocktakeBatch createBatch(StocktakeCreateRequest request);

    /** 录入或导入本次实盘并比对生成差异明细；已完成批次、重复编号明确阻止 */
    StocktakeBatch submitActuals(Long batchId, List<StocktakeActualLine> lines, String operator, String remark);

    /** 逐项确认差异；MISSING 缺失项必须填写缺失原因，其余项必须填写处理意见；已确认、已完成批次明确阻止 */
    StocktakeBatch confirmItem(Long batchId, Long itemId, StocktakeHandleRequest request);

    /** 对已确认项重新复核，回到待核状态并清空确认意见/缺失原因，记录复核轨迹 */
    StocktakeBatch recheckItem(Long batchId, Long itemId, StocktakeHandleRequest request);

    /** 批次完成；存在未核明细、缺失项未写缺失原因或已完成时明确阻止（提示点名资产编号） */
    StocktakeBatch completeBatch(Long batchId, StocktakeHandleRequest request);

    List<StocktakeBatch> findAll();

    StocktakeBatch findById(Long id);
}
