package com.example.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("repair_order")
public class RepairOrder {

    /** 待接单 */
    public static final String STATUS_PENDING = "PENDING";
    /** 维修中 */
    public static final String STATUS_IN_PROGRESS = "IN_PROGRESS";
    /** 已修复 */
    public static final String STATUS_FIXED = "FIXED";
    /** 无法修复 */
    public static final String STATUS_UNFIXABLE = "UNFIXABLE";

    /** 低 */
    public static final String URGENCY_LOW = "LOW";
    /** 一般 */
    public static final String URGENCY_NORMAL = "NORMAL";
    /** 紧急 */
    public static final String URGENCY_HIGH = "HIGH";
    /** 特急 */
    public static final String URGENCY_URGENT = "URGENT";

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("order_no")
    private String orderNo;

    @TableField("desk_chair_id")
    private Long deskChairId;

    /** 报修时所属分区快照，桌椅后续调区不影响工单归属 */
    @TableField("area_id")
    private Long areaId;

    @TableField("damage_part")
    private String damagePart;

    @TableField("urgency")
    private String urgency;

    @TableField("phenomenon")
    private String phenomenon;

    /** PENDING / IN_PROGRESS / FIXED / UNFIXABLE */
    @TableField("status")
    private String status;

    @TableField("reporter")
    private String reporter;

    @TableField("repairer")
    private String repairer;

    @TableField("accept_at")
    private LocalDateTime acceptAt;

    @TableField("finish_at")
    private LocalDateTime finishAt;

    @TableField("repair_note")
    private String repairNote;

    /** 闭环后处置：1 恢复可用 / 0 转停用，未处置为 null */
    @TableField("desk_status_after")
    private Integer deskStatusAfter;

    @TableField("handled_by")
    private String handledBy;

    @TableField("handled_at")
    private LocalDateTime handledAt;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    @TableField(exist = false)
    private String areaName;

    @TableField(exist = false)
    private String areaCode;

    @TableField(exist = false)
    private String assetCode;

    @TableField(exist = false)
    private Integer deskStatus;
}
