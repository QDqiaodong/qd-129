package com.example.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("area_change_batch_item")
public class AreaChangeBatchItem {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_FAILED = "FAILED";

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

    @TableField("old_area_id")
    private Long oldAreaId;

    @TableField("new_area_id")
    private Long newAreaId;

    @TableField("change_log_id")
    private Long changeLogId;

    @TableField("status")
    private String status;

    @TableField("error_message")
    private String errorMessage;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField(exist = false)
    private String oldAreaName;

    @TableField(exist = false)
    private String newAreaName;
}
