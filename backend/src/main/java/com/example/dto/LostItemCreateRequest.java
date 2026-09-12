package com.example.dto;

import lombok.Data;

@Data
public class LostItemCreateRequest {

    /** 所属阅览分区，须与桌椅当前分区一致 */
    private Long areaId;

    /** 捡到位置对应的桌椅 ID */
    private Long deskChairId;

    /** 物品名称 */
    private String itemName;

    /** 暂存位置 */
    private String storageLocation;

    /** 备注 */
    private String remark;

    /** 登记值班员 */
    private String operator;
}
