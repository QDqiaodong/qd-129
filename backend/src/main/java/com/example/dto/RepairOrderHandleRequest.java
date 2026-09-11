package com.example.dto;

import lombok.Data;

@Data
public class RepairOrderHandleRequest {

    /** 接单/维修处理人（接单时必填） */
    private String repairer;

    /** 维修说明 / 无法修复原因（完结时必填） */
    private String repairNote;

    /**
     * 闭环后桌椅处置：1 恢复可用 / 0 转停用。
     * 仅完结（已修复/无法修复）时使用，不填则保持停用，待后续处置。
     */
    private Integer deskStatusAfter;

    /** 后续“恢复可用/转停用”处置操作人 */
    private String operator;
}
