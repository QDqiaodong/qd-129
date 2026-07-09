package com.example.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName("desk_chair")
public class DeskChair {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("asset_code")
    private String assetCode;

    @TableField("capacity")
    private Integer capacity;

    @TableField("area_id")
    private Long areaId;

    @TableField("dimensions")
    private String dimensions;

    @TableField("status")
    private Integer status;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    @TableField(exist = false)
    private String areaName;

    @TableField(exist = false)
    private List<Tag> tags;
}