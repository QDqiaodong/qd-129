package com.example.dto;

import lombok.Data;

@Data
public class StocktakeCreateRequest {

    /** 盘点分区 */
    private Long areaId;

    /** 盘点备注 */
    private String remark;

    /** 操作人 */
    private String operator;
}
