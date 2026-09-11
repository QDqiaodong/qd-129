package com.example.dto;

import lombok.Data;

import java.util.List;

/**
 * 一条实盘录入：资产编号必填；实盘分区/启用状态/标签留空时按盘点分区与在册值兜底，
 * 其中标签留空表示本次未核对标签，不参与标签差异判断。
 */
@Data
public class StocktakeActualLine {

    /** 资产编号（必填） */
    private String assetCode;

    /** 实盘所在分区 ID，留空视为盘点分区 */
    private Long actualAreaId;

    /** 实盘启用状态：1 启用 / 0 停用，留空视为在册状态 */
    private Integer actualStatus;

    /** 实盘标签 ID 列表，null 表示未核对标签，空列表表示已核对为无标签 */
    private List<Long> actualTagIds;
}
