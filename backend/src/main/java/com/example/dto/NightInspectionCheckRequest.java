package com.example.dto;

import lombok.Data;

@Data
public class NightInspectionCheckRequest {

    /** 灯巡检结果：NORMAL 正常 / ABNORMAL 不亮，必填 */
    private String lightResult;

    /** 插座巡检结果：NORMAL 正常 / ABNORMAL 失灵，必填 */
    private String socketResult;

    /** 桌面巡检结果：NORMAL 正常 / ABNORMAL 涂鸦破损，必填 */
    private String deskSurfaceResult;

    /** 问题补充描述（可选） */
    private String problemDetail;

    /** 处理意见：任一项异常（有问题）时必填 */
    private String handleOpinion;

    /** 巡检登记人 */
    private String operator;
}
