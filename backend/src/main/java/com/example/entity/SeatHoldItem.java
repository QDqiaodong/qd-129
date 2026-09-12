package com.example.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("seat_hold_item")
public class SeatHoldItem {

    public static final String STATUS_HOLDING = "HOLDING";
    public static final String STATUS_TIMEOUT = "TIMEOUT";
    public static final String STATUS_RELEASED = "RELEASED";

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("batch_id")
    private Long batchId;

    @TableField("batch_no")
    private String batchNo;

    @TableField("desk_chair_id")
    private Long deskChairId;

    @TableField("asset_code")
    private String assetCode;

    /** 勾选时所属分区快照 */
    @TableField("area_id")
    private Long areaId;

    /** HOLDING 在占 / TIMEOUT 超时未到 / RELEASED 已释放 */
    @TableField("item_status")
    private String itemStatus;

    /** 占住前桌椅启用状态，释放时按此恢复 */
    @TableField("previous_desk_status")
    private Integer previousDeskStatus;

    @TableField("released_by")
    private String releasedBy;

    @TableField("released_at")
    private LocalDateTime releasedAt;

    @TableField("timeout_by")
    private String timeoutBy;

    @TableField("timeout_at")
    private LocalDateTime timeoutAt;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    @TableField(exist = false)
    private String areaName;

    /** 桌椅实时启用状态，刷新后与批次明细状态对照使用 */
    @TableField(exist = false)
    private Integer deskStatus;

    /** 跨批次查询在占资产时，所属批次的状态与分区（仅 findOpenHolds 使用） */
    @TableField(exist = false)
    private String batchStatus;

    @TableField(exist = false)
    private Long batchAreaId;

    @TableField(exist = false)
    private String areaCode;
}
