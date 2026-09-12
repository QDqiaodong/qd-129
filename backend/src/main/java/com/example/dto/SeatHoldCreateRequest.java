package com.example.dto;

import lombok.Data;

import java.util.List;

@Data
public class SeatHoldCreateRequest {

    /** 占座分区 */
    private Long areaId;

    /** 高峰时段 */
    private String timeSlot;

    /** 勾选占住的桌椅档案资产 ID 列表 */
    private List<Long> deskChairIds;

    /** 备注 */
    private String remark;

    /** 开批值班员 */
    private String operator;
}
