package com.example.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName("seat_hold_batch")
public class SeatHoldBatch {

    public static final String STATUS_OPEN = "OPEN";
    public static final String STATUS_ENDED = "ENDED";

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("batch_no")
    private String batchNo;

    @TableField("area_id")
    private Long areaId;

    /** 高峰时段，如 08:00-11:30 / 午间高峰，值班员开批时选定 */
    @TableField("time_slot")
    private String timeSlot;

    /** 本批勾选占住的资产总数 */
    @TableField("total_count")
    private Integer totalCount;

    /** 在占数量：明细状态 HOLDING */
    @TableField("held_count")
    private Integer heldCount;

    /** 超时未到数量：明细状态 TIMEOUT */
    @TableField("timeout_count")
    private Integer timeoutCount;

    /** 已释放数量：明细状态 RELEASED */
    @TableField("released_count")
    private Integer releasedCount;

    @TableField("remark")
    private String remark;

    @TableField("operator")
    private String operator;

    /** OPEN（进行中）/ ENDED（已结束） */
    @TableField("status")
    private String status;

    @TableField("ended_at")
    private LocalDateTime endedAt;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    @TableField(exist = false)
    private String areaName;

    @TableField(exist = false)
    private String areaCode;

    @TableField(exist = false)
    private List<SeatHoldItem> items;
}
