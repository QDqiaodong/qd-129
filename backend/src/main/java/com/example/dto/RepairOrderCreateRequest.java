package com.example.dto;

import lombok.Data;

@Data
public class RepairOrderCreateRequest {

    /** 已有桌椅 ID */
    private Long deskChairId;

    /** 所属分区，须与桌椅当前分区一致 */
    private Long areaId;

    /** 损坏部位 */
    private String damagePart;

    /** 紧急程度：LOW/NORMAL/HIGH/URGENT */
    private String urgency;

    /** 损坏现象 */
    private String phenomenon;

    /** 报修人 */
    private String reporter;
}
