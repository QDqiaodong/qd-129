package com.example.dto;

import lombok.Data;

@Data
public class NightInspectionCreateRequest {

    /** 巡检阅览分区 */
    private Long areaId;

    /** 开批值班员 */
    private String operator;

    /** 批次备注 */
    private String remark;
}
