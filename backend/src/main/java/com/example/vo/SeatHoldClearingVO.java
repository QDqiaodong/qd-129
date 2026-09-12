package com.example.vo;

import lombok.Data;

import java.util.List;

/**
 * 已结束占座批次的清场清单：把仍停用的桌椅分成三组，
 * 超时未到继续停用 / 应按占住前状态恢复 / 档案报修仍停用。
 */
@Data
public class SeatHoldClearingVO {

    private Long batchId;

    private String batchNo;

    /** 批次状态，清场清单仅对已结束（ENDED）批次生成 */
    private String batchStatus;

    /** 仍停用桌椅总数（三组之和） */
    private Integer stillDisabledCount;

    /** 应按占住前状态恢复：在占且无未闭环报修，清场释放后回写占住前状态 */
    private List<SeatHoldClearingItemVO> restorableItems;

    /** 超时未到继续停用：已标记超时未到且无未闭环报修，确认无人后逐条释放 */
    private List<SeatHoldClearingItemVO> timeoutDisabledItems;

    /** 档案报修仍停用：存在未闭环报修单，释放只闭环占座明细，桌椅保持停用 */
    private List<SeatHoldClearingItemVO> repairDisabledItems;
}
