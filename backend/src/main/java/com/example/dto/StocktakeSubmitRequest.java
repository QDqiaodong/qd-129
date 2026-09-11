package com.example.dto;

import lombok.Data;

import java.util.List;

@Data
public class StocktakeSubmitRequest {

    /** 本次实盘资产行（前端录入表格或导入解析后统一组装） */
    private List<StocktakeActualLine> lines;

    /** 操作人 */
    private String operator;

    /** 本次录入/导入说明，写入批次处理记录 */
    private String remark;
}
