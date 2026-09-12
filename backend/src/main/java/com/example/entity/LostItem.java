package com.example.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("lost_item")
public class LostItem {

    /** 待领取 */
    public static final String STATUS_PENDING = "PENDING";
    /** 已领取 */
    public static final String STATUS_CLAIMED = "CLAIMED";

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("item_no")
    private String itemNo;

    /** 登记时所属分区快照，桌椅后续调区不影响遗失单归属 */
    @TableField("area_id")
    private Long areaId;

    @TableField("desk_chair_id")
    private Long deskChairId;

    /** 桌椅资产编号快照 */
    @TableField("asset_code")
    private String assetCode;

    @TableField("item_name")
    private String itemName;

    @TableField("storage_location")
    private String storageLocation;

    /** PENDING / CLAIMED */
    @TableField("status")
    private String status;

    @TableField("remark")
    private String remark;

    @TableField("found_by")
    private String foundBy;

    @TableField("claimer_name")
    private String claimerName;

    /** 领取人核验信息（证件号/学工号等） */
    @TableField("claimer_verify")
    private String claimerVerify;

    @TableField("claim_conclusion")
    private String claimConclusion;

    @TableField("claimed_by")
    private String claimedBy;

    @TableField("claimed_at")
    private LocalDateTime claimedAt;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    @TableField(exist = false)
    private String areaName;

    @TableField(exist = false)
    private String areaCode;

    @TableField(exist = false)
    private Integer deskStatus;
}
