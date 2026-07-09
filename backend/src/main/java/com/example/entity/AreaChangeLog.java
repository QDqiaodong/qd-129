package com.example.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("area_change_log_00")
public class AreaChangeLog {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("desk_chair_id")
    private Long deskChairId;

    @TableField("old_area_id")
    private Long oldAreaId;

    @TableField("new_area_id")
    private Long newAreaId;

    @TableField("change_reason")
    private String changeReason;

    @TableField("operator")
    private String operator;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField(exist = false)
    private String assetCode;

    @TableField(exist = false)
    private String oldAreaName;

    @TableField(exist = false)
    private String newAreaName;
}