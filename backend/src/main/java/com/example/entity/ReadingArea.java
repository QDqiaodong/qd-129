package com.example.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("reading_area")
public class ReadingArea {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("area_code")
    private String areaCode;

    @TableField("area_name")
    private String areaName;

    @TableField("description")
    private String description;

    @TableField("status")
    private Integer status;

    /**
     * 今日闭馆结束时刻：非空且晚于当前时间表示该区正在闭馆；
     * 到期后该字段仍保留历史值，但不再产生闭馆效力，同分区可再开高峰占座
     */
    @TableField("closed_until")
    private LocalDateTime closedUntil;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    @TableField(exist = false)
    private Integer deskChairCount;
}